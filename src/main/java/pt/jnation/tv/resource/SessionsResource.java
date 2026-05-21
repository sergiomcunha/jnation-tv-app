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
import org.eclipse.microprofile.rest.client.inject.RestClient;
import pt.jnation.tv.AppConfig;
import pt.jnation.tv.AppConfig.RoomConfig;
import pt.jnation.tv.sessionize.Session;
import pt.jnation.tv.sessionize.SessionizeClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.groupingBy;

@Path("/sessions")
public class SessionsResource {
    @Inject
    AppConfig appConfig;
    @Inject
    @RestClient
    SessionizeClient sessionizeClient;

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance get() {
        return get(LocalDate.now());
    }

    @GET
    @Path("/{date}")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance get(@PathParam("date") final LocalDate date) {
        LocalDateTime now = ZonedDateTime.now(appConfig.zoneId())
                .withYear(date.getYear())
                .withMonth(date.getMonthValue())
                .withDayOfMonth(date.getDayOfMonth())
                .toLocalDateTime();

        List<Session> sessions = getSessions(now);
        if (sessions.isEmpty()) {
            return EmptyTemplate.empty();
        }

        return Templates.sessions(sessions);
    }

    List<Session> getSessions(final LocalDateTime now) {
        Map<String, List<Session>> sessionsByRoom =
                sessionizeClient.getSessions().stream()
                        .filter(session -> session.startsAt().toLocalDate().equals(now.toLocalDate()))
                        .collect(groupingBy(Session::room));

        List<Session> sessions = new ArrayList<>();
        for (RoomConfig room : appConfig.rooms()) {
            // Sessions are already ordered by time
            for (Session session : sessionsByRoom.getOrDefault(room.name(), List.of())) {
                if (now.isBefore(session.endsAt())) {
                    sessions.add(session);
                    break;
                }
            }

            if (!sessions.isEmpty() && sessions.getFirst().isPlenumSession()) {
                break;
            }
        }

        if (!sessions.isEmpty()) {
            for (Session session : sessions) {
                sessionizeClient.getSessionWithSpeakers(session);
                session.roomCssColor().setCss(appConfig.findRoom(session.room()).cssColor());
            }
        }

        return sessions;
    }

    @CheckedTemplate
    public static class Templates {
        @Location("sessions.html")
        public static native TemplateInstance sessions(List<Session> sessions);
    }
}
