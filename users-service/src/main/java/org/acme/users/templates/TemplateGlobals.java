package org.acme.users.templates;

import io.quarkus.qute.TemplateGlobal;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Globals de template para os links da página renderizada.
 * <p>
 * <code>rootPath</code> devolve o valor de {@code quarkus.http.root-path}
 * normalizado (sem barra final; vazio quando {@code /}), para que as URLs
 * absolutas dos templates (webjars, HTMX, logout) apontem para o caminho
 * correto tanto no modo local quanto atrás do prefixo {@code /users} do
 * traefik no agregador.
 */
@TemplateGlobal
public class TemplateGlobals {

    @TemplateGlobal
    public static String rootPath() {
        String root = ConfigProvider.getConfig()
                .getOptionalValue("quarkus.http.root-path", String.class)
                .orElse("/");
        if (root == null || root.isBlank() || "/".equals(root)) {
            return "";
        }
        return root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
    }
}