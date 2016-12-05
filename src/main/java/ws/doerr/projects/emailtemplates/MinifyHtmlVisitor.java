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

import org.jsoup.nodes.Comment;
import org.jsoup.nodes.DocumentType;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeVisitor;

/**
 * Jsoup Node Visitor to extract minified HTML
 *
 * @author Greg Doerr <greg@doerr.ws>
 */
class MinifyHtmlVisitor implements NodeVisitor {
    private final StringBuilder sb = new StringBuilder();
    private final boolean removeComments;
    
    public MinifyHtmlVisitor() {
        this(false);
    }
    
    public MinifyHtmlVisitor(boolean removeComments) {
        this.removeComments = removeComments;
    }

    String getHtml() {
        return sb.toString();
    }

    @Override
    public void head(Node node, int i) {
        if(node instanceof DocumentType) {
            DocumentType dt = (DocumentType) node;
            sb.append("<!DOCTYPE html PUBLIC");

            if(dt.hasAttr("publicId"))
                sb.append(' ').append(dt.attr("publicId"));

            if(dt.hasAttr("systemId"))
                sb.append(' ').append(dt.attr("systemId"));

            sb.append('>');
        } else if(node instanceof Element) {
//            if(removeComments && "#comment".equals(node.nodeName()))
//                return;
            
            Element e = (Element) node;

            if("#root".equals(e.tagName()))
                return;

            sb.append('<')
                .append(e.tagName());

            sb.append(e.attributes().html());

            if(e.childNodes().isEmpty() && e.tag().isSelfClosing()) {
                if(e.tag().isEmpty())
                    sb.append('>');
                else
                    sb.append(" />");
            } else
                sb.append('>');
        } else if(node instanceof TextNode) {
            TextNode tn = (TextNode) node;

            sb.append(tn.getWholeText().replaceAll("\\s+", " "));
        } else if(node instanceof Comment) {
            if(!removeComments) {
                Comment c = (Comment) node;

                sb.append("<!--")
                    .append(c.getData().replaceAll("\\s+", " "))
                    .append("-->");
            }
        }
    }

    @Override
    public void tail(Node node, int i) {
        if(node instanceof Element) {
            Element e = (Element) node;

            if("#root".equals(e.tagName()))
                return;

            if(!(e.childNodes().isEmpty() && e.tag().isSelfClosing()))
                sb.append("</").append(e.tagName()).append('>');
        }
    }
}
