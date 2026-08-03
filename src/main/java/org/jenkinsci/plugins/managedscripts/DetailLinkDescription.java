package org.jenkinsci.plugins.managedscripts;

import hudson.model.Item;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.configfiles.utils.DescriptionResponse;
import org.kohsuke.stapler.StaplerRequest;

public class DetailLinkDescription extends DescriptionResponse {
    private DetailLinkDescription(String linkHtml) {
        super(linkHtml);
    }

    public static DetailLinkDescription getDescription(StaplerRequest req, Item context, String fileId, String argumentDetails) {
        return new DetailLinkDescription(getDetailsLink(req, context, fileId, argumentDetails));
    }

    private static String getDetailsLink(StaplerRequest req, Item context, String fileId, String argumentDetails) {
        String link = req.getContextPath();
        link = (context.getUrl() != null && !context.getUrl().trim().isEmpty()) ? link + "/" + context.getUrl() : link;
        link = link + "configfiles/show?id=" + fileId;
        String html = "<a target=\"_blank\" href=\"" + link + "\">view selected file</a>";

        if (argumentDetails != null && !argumentDetails.trim().isEmpty()) {
            html = html + "<br />" + argumentDetails;
        }

        // 1x16 spacer needed for IE since it doesn't support min-height
        html = "<div class='ok'><img src='" +
                req.getContextPath() + Jenkins.RESOURCE_PATH + "/images/none.gif' height=16 width=1>" +
                html + "</div>";

        return html;
    }

}
