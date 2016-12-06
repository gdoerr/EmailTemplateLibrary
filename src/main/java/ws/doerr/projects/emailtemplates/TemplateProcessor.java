/*
 *  **************************************************************************
 *                     Copyright © 2016-2016 Credico USA, LLC
 *                            All Rights Reserved.
 *      ------------------------------------------------------------------
 *
 *      Credico USA, LLC
 *      525 West Monroe, Suite 900
 *      Chicago, IL 60661
 *      software@credicousa.com
 *
 *      ------------------------------------------------------------------
 *  Copyright © 2014-2016. Credico USA, LLC. All Rights Reserved.
 *  Permission to use, copy, modify, and distribute this software and its
 *  documentation for educational, research, and not-for-profit purposes,
 *  without fee and without a signed licensing agreement, is hereby granted,
 *  provided that this copyright notice, this paragraph and the following two
 *  paragraphs appear in all copies, modifications, and distributions.
 *  Contact Credico USA, LLC. at software@credicousa.com for
 *  commercial licensing opportunities.
 *
 *  IN NO EVENT SHALL CREDICO USA, LLC. BE LIABLE TO ANY PARTY FOR DIRECT,
 *  INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST
 *  PROFITS, ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION,
 *  EVEN IF CREDICO HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *  CREDICO USA, LLC. SPECIFICALLY DISCLAIMS ANY WARRANTIES, INCLUDING, BUT NOT
 *  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 *  PARTICULAR PURPOSE. THE SOFTWARE AND ACCOMPANYING DOCUMENTATION, IF ANY,
 *  PROVIDED HEREUNDER IS PROVIDED "AS IS". CREDICO USA, LLC. HAS NO OBLIGATION
 *  TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR MODIFICATIONS.
 *  **************************************************************************
 */
package ws.doerr.projects.emailtemplates;

import com.google.common.base.Charsets;
import com.steadystate.css.parser.CSSOMParser;
import com.steadystate.css.parser.SACParserCSS3;
import org.jsoup.Jsoup;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.jsoup.select.NodeTraversor;
import org.w3c.css.sac.InputSource;
import org.w3c.dom.css.CSSRule;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSStyleRule;
import org.w3c.dom.css.CSSStyleSheet;

/**
 * HTML Email Template Processor
 *
 * Provides an flexible template engine to generate and maintain email templates
 *
 * Includes templates, includes with parameters, selective inlining and
 * HTML minification.
 *
 * Templates loosely based on JSF concepts
 *
 */
public class TemplateProcessor {
    private static final Logger LOG = Logger.getLogger(TemplateProcessor.class.getName());

    private static final String STYLE_TAG = "style";
    private static final String LINK_INLINE_ATTR = "ui:inline";

    private static final String LINK_TAG = "LINK";
    private static final String LINK_REL_ATTR = "rel";
    private static final String LINK_REL_STYLE = "stylesheet";
    private static final String LINK_REL_IMPORT = "import";
    private static final String LINK_HREF_ATTR = "href";

    private static final String PARAMETER_TAG = "parameter";
    private static final String PARAMETER_NAME_ATTR = "name";
    private static final String PARAMETER_ATTR_NAME = "attr";

    private static final String TEMPLATE_ATTR = "ui:template";
    private static final String SECTION_TAG = "ui:section";
    private static final String INCLUDE_TAG = "ui:include";

    private final Parser parser;

    private final Map<String, String> addMeta = new HashMap<>();
    private final boolean removeComments;

    /**
     * Create the default Processor
     *
     * No version meta tag will be added and html comments will not be removed
     */
    public TemplateProcessor() {
        this(Collections.emptyMap(), false);
    }

    /**
     * Create a processor with properties
     *
     * @param meta meta tags to add to the template header
     * @param removeComments if true, remove any html comments from the output
     */
    public TemplateProcessor(Map<String, String> meta, boolean removeComments) {
        addMeta.putAll(meta);
        this.removeComments = removeComments;
        parser = Parser.xmlParser();
    }

    /**
     * Process a source file
     *
     * @param source path of the file to process
     * @return the resulting context from the processing operation
     * @throws Exception resulting from the processing of the source
     */
    public ProcessorContext process(final Path source) throws Exception {
        return process(source, null);
    }

    /**
     * Process a source file to an output path
     * @param source source file path
     * @param destination destination directory path
     * @return the resulting context from the processing operation
     * @throws Exception resulting from the processing of the source
     */
    public ProcessorContext process(final Path source, final Path destination) throws Exception {
        try (InputStream input = Files.newInputStream(source)) {
            ProcessorContext context = process(input, source.getParent());

            if(destination != null)
                Files.write(destination, context.getHtml().getBytes(Charsets.UTF_8));

            return context;
        }
    }

    /**
     * Process a source input stream
     * @param input input source stream
     * @param relative relative path for opening included files
     * @return the resulting context from the processing operation
     * @throws Exception resulting from the processing of the source
     */
    public ProcessorContext process(final InputStream input, Path relative) throws Exception {
        // If relative is null, default to the current working directory
        if(relative == null)
            relative = Paths.get(".");

        ProcessorContext context = new ProcessorContext();

        // Parse the source document
        Document doc = Jsoup.parse(
                input,
                Charsets.UTF_8.name(),
                "",
                parser);

        // Process linked documents
        while(processLinks(doc, relative.toString(), context)) {}

        // Check for template use
        Element html = doc.getElementsByTag("html").first();
        if(html.hasAttr(TEMPLATE_ATTR)) {
            // The document uses a template, parse it.
            Path tmpl = relative.resolve(html.attr(TEMPLATE_ATTR));

            try (InputStream tinput = Files.newInputStream(tmpl)) {
                Document template = Jsoup.parse(
                        tinput,
                        Charsets.UTF_8.name(),
                        "",
                        parser);

                // Mark the dependency
                context.addDependency(tmpl, Dependency.DependencyType.Template);

                // Move the head items from the document into the template
                template.head().prepend(doc.head().html());

                // Process the ui:section source tags
                doc.getElementsByTag(SECTION_TAG).forEach(section -> {
                    String name = section.attr("name");

                    // Find the corresponding insert points in the template
                    Element match = find(template, INCLUDE_TAG, "section", name);
                    if(match != null) {
                        // Copy the content to the template and remove the insert tag
                        match.after(section.html());
                        match.remove();
                    }
                });

                // The template becomes the new document
                doc = template;

                // Process any linked documents from the template
                while(processLinks(doc, relative.toString(), context)) {}
            } catch(Exception ex) {}
        }

        // Inline the styles
        extractAndApplyStyles(doc);

        // Add any additional meta tags
        for(Entry<String, String> meta : addMeta.entrySet()) {
            doc.head().appendElement("meta").attr("name", meta.getKey()).attr("content", meta.getValue());
        }

        // Gather the metadata
        extractMeta(doc, context);

        context.setTitle(doc.title());

        // Use a custom node visitor to extract minified html
        MinifyHtmlVisitor visitor = new MinifyHtmlVisitor(removeComments);
        new NodeTraversor(visitor).traverse(doc);

        context.setHtml(visitor.getHtml());

        return context;
    }

    /**
     * Extract the title from a document
     * @param source path to the source file
     * @return the title of the document as a String
     */
    public String getTitle(Path source) {
        Document doc;
        try (InputStream input = new FileInputStream(source.toFile())) {
            doc = Jsoup.parse(
                    input,
                    Charsets.UTF_8.name(),
                    "",
                    parser);

            return doc.title();
        } catch(IOException ex) {
            return "";
        }

    }

    /**
     * Extract the meta-data from the document
     *
     * @param doc base parsed template
     * @param meta context to write the meta data to
     */
    private void extractMeta(Element doc, ProcessorContext context) {
        doc.getElementsByTag("meta").stream()
                .filter((element) -> {
                    return element.hasAttr("name") && element.hasAttr("content");
                })
                .forEach((element) -> {
                    context.addMeta(element.attr("name"), element.attr("content"));
                });
    }

    /**
     * Process any links in the document
     * @param doc base parsed document
     * @param basePath base path for relative referenced includes
     * @param context processor context
     * @return true if files were included
     */
    private boolean processLinks(Element doc, String basePath, ProcessorContext context) {
        boolean processed = false;

        for(Element element : doc.getElementsByTag(LINK_TAG)) {
            // Determine the element type
            boolean isStylesheet = LINK_REL_STYLE.equals(element.attr(LINK_REL_ATTR));
            boolean isImport = LINK_REL_IMPORT.equals(element.attr(LINK_REL_ATTR));

            // We only support Stylesheets and html imports
            if(!isStylesheet && !isImport)
                continue;

            // Have to have an HREF
            String elementPath = element.attr(LINK_HREF_ATTR);
            if(elementPath == null) {
                LOG.log(Level.WARNING, "Missing required 'href' attribute for include tag {0}", element.text());
                continue;
            }

            try {
                // We need to process the import tags from the inside out so we
                // process this tag until any contained tags are processed
                if(isImport && hasChildTag(element, LINK_TAG))
                    continue;

                // Grab the source from the reference
                Path path = FileSystems.getDefault().getPath(basePath, elementPath);

                element.removeAttr(LINK_REL_ATTR);
                element.removeAttr(LINK_HREF_ATTR);

                if(isStylesheet) {
                    // Including a stylesheet
                    Element style = new Element(Tag.valueOf("style"), element.baseUri(), element.attributes());
                    style.text(new String(Files.readAllBytes(path), Charsets.UTF_8));
                    element.after(style);

                    context.addDependency(path, element.hasAttr(LINK_INLINE_ATTR) ?
                            Dependency.DependencyType.StyleInline :
                            Dependency.DependencyType.Style);
                } else {
                    Map<String, String> parameters = new HashMap<>();

                    // Fetch any include parameters
                    for(Element child : element.children()) {
                        if(PARAMETER_TAG.equals(child.tagName())) {
                            if(!child.hasAttr(PARAMETER_NAME_ATTR))
                                LOG.log(Level.WARNING, "Missing 'name' attribute for include parameter in {0}", elementPath);
                            else {
                                parameters.put(child.attr(PARAMETER_NAME_ATTR), child.html());
                            }
                        }
                    }

                    // Process the include file
                    try (InputStream input = new FileInputStream(path.toFile())) {
                        Element inserted = Jsoup.parse(input, Charsets.UTF_8.name(), "", parser);

                        if(!parameters.isEmpty()) {
                            applyParameters(inserted, parameters);
                            handleConditionalComments(inserted, parameters);
                        }

                        processLinks(inserted, path.getParent().toString(), context);
                        element.after(inserted);
                        inserted.unwrap();
                    }

                    context.addDependency(path, Dependency.DependencyType.Fragment);
                }

                processed = true;

                element.remove();
            } catch(Exception ex) {
                LOG.log(Level.WARNING, "Exception processing tag " + element.text(), ex);
            }
        }

        return processed;
    }

    /**
     * Apply parameters to the imported fragment
     *
     * @param element imported element
     * @param parameters parameters from the import statement
     */
    void applyParameters(Element element, Map<String, String> parameters) {
        for(Element parameter : element.getElementsByTag(PARAMETER_TAG)) {
            if(parameter.hasAttr(PARAMETER_NAME_ATTR)) {
                String name = parameter.attr(PARAMETER_NAME_ATTR);
                if(!parameters.containsKey(name))
                    LOG.log(Level.WARNING, "No value for {0}", name);
                else if(parameter.hasAttr(PARAMETER_ATTR_NAME)) {
                    // Set the value as an attribute on the parent
                    String attrName = parameter.attr(PARAMETER_ATTR_NAME);

                    if(parameter.parent().hasAttr(attrName))
                        parameter.parent().attr(attrName, parameter.parent().attr(attrName) + " " + parameters.get(name));
                    else
                        parameter.parent().attr(attrName, parameters.get(name));

                    parameter.remove();
                } else {
                    // Replace the parameter tag with the value
                    parameter.after(parameters.get(name));
                    parameter.remove();
                }
            }
        }
    }

    /**
     * Handle Conditional Comments
     *
     * Conditional comments are sometimes used to selectively activate different
     * html fragments depending on the viewing environment. For our purposes, this
     * is most often done to supply custom html fragments to ensure proper
     * rendering in Microsoft Office.
     *
     * We look for comments in the included fragments, extract the content from
     * the comment, parse the content into a proper DOM and then replace the
     * parameters. We then use the original comment opener & closer to render
     * a replacement comment tag with the revised content.
     *
     * @param node html node potentially containing conditional comments
     * @param parameters the current parameter list
     */
    private void handleConditionalComments(Node node, Map<String, String> parameters) {
        for(int i = 0; i < node.childNodeSize(); i++) {
            Node child = node.childNode(i);
            if(child.nodeName().equals("#comment")) {
                String comment = Comment.class.cast(child).getData();

                try {
                    String open = comment.substring(0, comment.indexOf('>') + 1);
                    int closeIdx = comment.indexOf("<!");
                    String close = comment.substring(closeIdx);

                    String body = comment.replace(open, "").replace(close, "");

                    Element htmlBody = Jsoup.parse(body, "", parser);
                    applyParameters(htmlBody, parameters);

                    child.replaceWith(new Comment(open + htmlBody.html() + close, ""));
                } catch(Exception ex) {}
            } else {
                handleConditionalComments(child, parameters);
            }
        }
    }

    /**
     * Extract and inline styles
     *
     * Finds any styles included in the documents, inlines them into the resulting
     * html and removes the styles
     *
     * @param doc parsed html document
     * @throws IOException exception parsing included styles
     */
    void extractAndApplyStyles(Document doc) throws IOException {
        StringBuilder builder = new StringBuilder();

        for(Element element : doc.getElementsByTag(STYLE_TAG)) {
            if(element.hasAttr(LINK_INLINE_ATTR)) {
                for(Node node : element.childNodes())
                    builder.append(((TextNode)node).getWholeText());
                element.remove();
            }
        }

        CSSOMParser parser = new CSSOMParser(new SACParserCSS3());
        InputSource src = new InputSource(new StringReader(builder.toString()));
        CSSStyleSheet sheet = parser.parseStyleSheet(src, null, null);

        Map<Element, Map<String, String>> elementStyles = new HashMap<>();

        for(int i = 0; i < sheet.getCssRules().getLength(); i++) {
            CSSRule rule = sheet.getCssRules().item(i);
            if(rule instanceof CSSStyleRule) {
                CSSStyleRule style = (CSSStyleRule) rule;
                String selector = style.getSelectorText();

                if(!selector.contains(":")) {
                    Elements selectedElements = doc.select(selector);

                    for (Element selected : selectedElements) {
                        if (!elementStyles.containsKey(selected))
                            elementStyles.put(selected, new LinkedHashMap<>());

                        CSSStyleDeclaration styleDeclaration = style.getStyle();

                        for (int j = 0; j < styleDeclaration.getLength(); j++) {
                            String propertyName = styleDeclaration.item(j);
                            Map<String, String> elementStyle = elementStyles.get(selected);
                            elementStyle.put(propertyName, styleDeclaration.getPropertyValue(propertyName));
                        }
                    }
                }
            }
        }

        for (final Map.Entry<Element, Map<String, String>> elementEntry : elementStyles.entrySet()) {
            Element element = elementEntry.getKey();

            builder.setLength(0);

            for (final Map.Entry<String, String> styleEntry : elementEntry.getValue().entrySet())
                builder.append(styleEntry.getKey()).append(":").append(styleEntry.getValue()).append(";");

            builder.append(element.attr("style"));
            element.attr("style", builder.toString());
            element.removeAttr("class");
        }
    }

    /**
     * Utility Function - get the child element with the specified tag and
     * attribute value. We don't use the JSoup select because our specific
     * tag contains illegal characters (:).
     *
     * @param element starting node for the search
     * @param tag tag name to look for
     * @param attr attribute name to look for
     * @param attrValue attribute value to look for
     * @return the first found element matching the parameters or null if not found
     */
    private Element find(Element element, String tag, String attr, String attrValue) {
        for(Element m : element.getElementsByTag(tag)) {
            if(m.hasAttr(attr) && m.attr(attr).equals(attrValue))
                return m;
        }

        return null;
    }

    /**
     * Utility Function - see if an element has a specific CHILD tag.
     *
     * getElementsByTag includes the current element if it matches so we filter
     * that out here
     *
     * @param element starting node for the search
     * @param tag tag name to look for
     * @return true if the element has a CHILD element with the tag
     */
    private boolean hasChildTag(Element element, String tag) {
        for(Element child : element.getElementsByTag(tag)) {
            if(child != element)
                return true;
        }

        return false;
    }
}
