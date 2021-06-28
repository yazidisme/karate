/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.template;

import com.intuit.karate.StringUtils;
import com.intuit.karate.graal.JsEngine;
import com.intuit.karate.http.RequestCycle;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.IContext;
import org.thymeleaf.context.IEngineContext;
import org.thymeleaf.context.StandardEngineContextFactory;
import org.thymeleaf.dialect.IDialect;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.engine.TemplateManager;
import org.thymeleaf.exceptions.TemplateOutputException;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.util.FastStringWriter;

/**
 *
 * @author pthomas3
 */
public class KarateTemplateEngine {

    private static final Logger logger = LoggerFactory.getLogger(KarateTemplateEngine.class);

    private final StandardEngineContextFactory standardFactory;
    private final TemplateEngine wrapped;

    public KarateTemplateEngine(JsEngine je, IDialect... dialects) {
        standardFactory = new StandardEngineContextFactory();
        wrapped = new TemplateEngine();
        wrapped.setEngineContextFactory((IEngineConfiguration ec, TemplateData data, Map<String, Object> attrs, IContext context) -> {
            IEngineContext engineContext = standardFactory.createEngineContext(ec, data, attrs, context);
            return TemplateEngineContext.initThreadLocal(engineContext, je == null ? RequestCycle.get().getEngine() : je);
        });
        // the next line is a set which clears and replaces all existing / default
        wrapped.setDialect(new KarateStandardDialect());
        for (IDialect dialect : dialects) {
            wrapped.addDialect(dialect);
        }
    }

    public void setTemplateResolver(ITemplateResolver templateResolver) {
        wrapped.setTemplateResolver(templateResolver);
    }

    public String process(String template) {
        return process(template, TemplateContext.LOCALE_US);
    }

    public String process(String template, IContext context) {
        TemplateSpec templateSpec = new TemplateSpec(template, TemplateMode.HTML);
        Writer stringWriter = new FastStringWriter(100);
        process(templateSpec, context, stringWriter);
        return stringWriter.toString();
    }

    public void process(TemplateSpec templateSpec, IContext context, Writer writer) {
        try {
            TemplateManager templateManager = wrapped.getConfiguration().getTemplateManager();
            templateManager.parseAndProcess(templateSpec, context, writer);
            try {
                writer.flush();
            } catch (IOException e) {
                throw new TemplateOutputException("error flushing output writer", templateSpec.getTemplate(), -1, -1, e);
            }
        } catch (Exception e) {
            // make thymeleaf errors easier to troubleshoot from the logs
            while (e.getCause() instanceof Exception) {
                e = (Exception) e.getCause();
                if (e instanceof TemplateProcessingException) {                    
                    if (e.getCause() != null) { // typically the js error
                        String message = e.getCause().getMessage();
                        if (message != null && message.startsWith("redirect")) { // TODO improve
                            break; // don't log a redirect as an error
                        }
                        logger.error("{}", message);
                    }                    
                    logger.error("{}", e.getMessage()); // will print line and col numbers
                    break;
                }
            }
            if (logger.isTraceEnabled()) {
                logger.trace("{}", StringUtils.throwableToString(e));
            }
            throw new RuntimeException(e);
        }
    }

}
