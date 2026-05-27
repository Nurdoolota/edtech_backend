package com.lms.auth.email;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

public class EmailTemplateRenderer {

    private final TemplateEngine htmlTemplateEngine;
    private final TemplateEngine textTemplateEngine;

    public EmailTemplateRenderer(TemplateEngine htmlTemplateEngine, TemplateEngine textTemplateEngine) {
        this.htmlTemplateEngine = htmlTemplateEngine;
        this.textTemplateEngine = textTemplateEngine;
    }

    public String renderHtml(String templateName, Map<String, Object> variables) {
        Context ctx = new Context();
        ctx.setVariables(variables);
        return htmlTemplateEngine.process("emails/" + templateName, ctx);
    }

    public String renderText(String templateName, Map<String, Object> variables) {
        Context ctx = new Context();
        ctx.setVariables(variables);
        return textTemplateEngine.process("emails/" + templateName, ctx);
    }
}
