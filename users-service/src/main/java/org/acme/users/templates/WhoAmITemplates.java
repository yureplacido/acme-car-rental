package org.acme.users.templates;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

@CheckedTemplate(basePath = "WhoAmITemplates")
public class WhoAmITemplates {

    public static native TemplateInstance whoami(String name);
}