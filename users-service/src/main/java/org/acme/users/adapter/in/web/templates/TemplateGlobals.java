package org.acme.users.adapter.in.web.templates;

import io.quarkus.qute.TemplateGlobal;
import org.eclipse.microprofile.config.ConfigProvider;

@TemplateGlobal
public class TemplateGlobals {

    @TemplateGlobal
    public static String rootPath() {
        String root = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.root-path", String.class)
                .orElse("/");
        if (root == null || root.isBlank() || "/".equals(root)) return "";
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    }
}
