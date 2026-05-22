package pt.jnation.tv.resource;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.Location;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate
public class EmptyTemplate {
    @Location("empty.html")
    public static native TemplateInstance empty();
}
