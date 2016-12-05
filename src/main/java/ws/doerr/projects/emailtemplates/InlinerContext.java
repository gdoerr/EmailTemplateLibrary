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

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author greg
 */
public class InlinerContext {
    private final Set<Dependency> dependencies = new HashSet<>();
    private final Map<String, String> meta = new HashMap<>();
    private String html;
    private String title;

    public Set<Dependency> getDependencies() {
        return dependencies;
    }

    public Map<String, String> getMeta() {
        return meta;
    }

    protected void addDependency(Path path, Dependency.DependencyType type) {
        dependencies.add(new Dependency(path, type));
    }

    protected void addMeta(String label, String value) {
        meta.put(label, value);
    }

    protected void setHtml(String html) {
        this.html = html;
    }

    public String getHtml() {
        return html;
    }
    
    public String getTitle() {
        return title;
    }

    protected void setTitle(String title) {
        this.title = title;
    }
}
