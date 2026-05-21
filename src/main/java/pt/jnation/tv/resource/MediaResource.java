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
import pt.jnation.tv.AppConfig;

@Path("/media")
public class MediaResource {
    @Inject
    AppConfig appConfig;

    private static final java.util.regex.Pattern IMAGE_EXT = java.util.regex.Pattern.compile("(?i).*\\.(jpg|jpeg|png|gif|webp|svg)$");

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance get() {
        String url = appConfig.media().get("default").next().toString();
        return Templates.media(url, IMAGE_EXT.matcher(url).matches());
    }

    @GET
    @Path("/{type}")
    @Produces(MediaType.TEXT_HTML)
    @Blocking
    public TemplateInstance get(@PathParam("type") final String type) {
        String url = appConfig.media().get(type).next().toString();
        return Templates.media(url, IMAGE_EXT.matcher(url).matches());
    }

    @CheckedTemplate
    public static class Templates {
        @Location("media.html")
        public static native TemplateInstance media(String url, boolean isImage);
    }
}
