package pt.jnation.tv.resource;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.Location;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pt.jnation.tv.AppConfig;
import pt.jnation.tv.AppConfig.RoomConfig;
import pt.jnation.tv.sessionize.Room;
import pt.jnation.tv.sessionize.Schedule;
import pt.jnation.tv.sessionize.Session;
import pt.jnation.tv.sessionize.SessionizeClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Path("/room")
public class RoomsResource {
    @Inject
    AppConfig appConfig;
    @Inject
    @RestClient
    SessionizeClient sessionizeClient;

    @GET
    @Path("/{name}")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance get(@PathParam("name") final String name) {
        return get(name, LocalDate.now());
    }

    @GET
    @Path("/{name}/{date}")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance get(@PathParam("name") final String name, @PathParam("date") final LocalDate date) {
        LocalDateTime now = ZonedDateTime.now(appConfig.zoneId())
                .withYear(date.getYear())
                .withMonth(date.getMonthValue())
                .withDayOfMonth(date.getDayOfMonth())
                .toLocalDateTime();
        SessionResponse session = getSession(name, now);
        if (session.hasSession()) {
            RoomConfig roomConfig = appConfig.findRoom(name);
            return Templates.sessionCard(session.getSession(), roomConfig);
        } else {
            return EmptyTemplate.empty();
        }
    }

    SessionResponse getSession(final String name, final LocalDateTime now) {
        Optional<Schedule> schedule = sessionizeClient.getSchedule(now.toLocalDate());
        if (schedule.isEmpty()) {
            return SessionResponse.error("Schedule Not Found!");
        }

        RoomConfig roomConfig = appConfig.findRoom(name);
        Optional<Room> room = schedule.get().rooms()
                .stream()
                .filter(r -> roomConfig.name().equals(r.name()))
                .findFirst();
        if (room.isEmpty()) {
            return SessionResponse.error("Room Not Found!");
        }

        List<Session> sessions = room.get().sessions()
                .stream()
                .filter(session -> !session.speakers().isEmpty())
                .sorted(Comparator.comparing(Session::startsAt))
                .toList();
        if (sessions.isEmpty()) {
            return SessionResponse.error("Sessions Not Found!");
        }

        for (Session session : sessions) {
            if (now.isBefore(session.endsAt())) {
                return SessionResponse.ok(sessionizeClient.getSessionWithSpeakers(session));
            }
        }

        return SessionResponse.error("No more Sessions for Today!");
    }

    @GET
    @Path("/{name}/timer")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance timer(@PathParam("name") final String name) {
        RoomConfig roomConfig = appConfig.findRoom(name);
        return Templates.timer(name, roomConfig, null);
    }

    @GET
    @Path("/{name}/timer/{date}")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance timer(@PathParam("name") final String name, @PathParam("date") final LocalDate date) {
        RoomConfig roomConfig = appConfig.findRoom(name);
        return Templates.timer(name, roomConfig, date);
    }

    @GET
    @Path("/{name}/timer/data")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response timerData(@PathParam("name") final String name) {
        LocalDateTime now = ZonedDateTime.now(appConfig.zoneId()).toLocalDateTime();
        return buildTimerData(name, now);
    }

    @GET
    @Path("/{name}/timer/data/{date}")
    @Produces(MediaType.APPLICATION_JSON)
    @Blocking
    public Response timerData(@PathParam("name") final String name, @PathParam("date") final LocalDate date) {
        LocalDateTime now = ZonedDateTime.now(appConfig.zoneId())
                .withYear(date.getYear())
                .withMonth(date.getMonthValue())
                .withDayOfMonth(date.getDayOfMonth())
                .toLocalDateTime();
        return buildTimerData(name, now);
    }

    private Response buildTimerData(final String name, final LocalDateTime now) {
        SessionResponse session = getSession(name, now);
        if (session.hasSession()) {
            Session s = session.getSession();
            return Response.ok(new TimerData(s.title(),
                    s.speakers().stream().map(sp -> new TimerSpeaker(sp.fullName(), sp.profilePicture())).toList(),
                    s.startsAt().toString(), s.endsAt().toString(), now.toString())).build();
        } else {
            return Response.ok(new TimerData(null, List.of(), null, null, now.toString())).build();
        }
    }

    public record TimerData(String title, List<TimerSpeaker> speakers, String startsAt, String endsAt, String serverTime) {}
    public record TimerSpeaker(String fullName, java.net.URI profilePicture) {}

    @CheckedTemplate
    public static class Templates {
        @Location("session-card.html")
        public static native TemplateInstance sessionCard(Session session, RoomConfig roomConfig);

        public static native TemplateInstance timer(String room, RoomConfig roomConfig, LocalDate date);
    }

    public static class SessionResponse {
        private final Session session;
        private final String error;

        SessionResponse(Session session, String error) {
            this.session = session;
            this.error = error;
        }

        boolean hasSession() {
            return session != null;
        }

        public Session getSession() {
            return session;
        }

        public String getError() {
            return error;
        }

        static SessionResponse ok(Session session) {
            return new SessionResponse(session, null);
        }

        static SessionResponse error(String error) {
            return new SessionResponse(null, error);
        }
    }
}
