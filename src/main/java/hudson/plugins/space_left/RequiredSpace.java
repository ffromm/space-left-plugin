package hudson.plugins.space_left;

import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Node;
import hudson.model.TopLevelItem;
import hudson.remoting.VirtualChannel;
import hudson.slaves.WorkspaceList;
import jenkins.model.Jenkins;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.io.File;
import java.io.IOException;

/**
 * Represents the space required by all connected workspaces on the given node/slave.
 */
public class RequiredSpace {

    /**
     * The token that combines the project name and unique number to create unique workspace directory.
     */
    private static final String COMBINATOR = System.getProperty(WorkspaceList.class.getName(),"@");

    /**
     * the node to get the required space for.
     */
    private Node node;

    /**
     * Constructor with node to analyse
     */
    public RequiredSpace(Node node) {
        this.node = node;
    }

    /**
     * Returns the required space of all connected workspaces.
     * @return the required space
     */
    public long getRequiredSpace(AbstractProject currentProject) throws IOException, InterruptedException {
        long requiredSpace = 0L;

        FilePath p = this.node.getRootPath();

        if (p == null) {
            return requiredSpace;
        }

        FilePath workspace = p.child("workspace");

        if (workspace.exists()) {
            for (FilePath projectDir : workspace.listDirectories()) {
                TopLevelItem topLevelItem = Jenkins.getInstance().getItem(projectDir.getName());

                if(topLevelItem == null && projectDir.getName().contains(COMBINATOR)) {
                    String projectName = projectDir.getName();
                    topLevelItem = Jenkins.getInstance().getItem(projectName.substring(0, projectName.lastIndexOf(COMBINATOR)));
                }

                if (topLevelItem instanceof AbstractProject) {
                    AbstractProject project = (AbstractProject) topLevelItem;
                    if(currentProject == null || !project.getName().equals(currentProject.getName()))
                    {
                        requiredSpace += this.getRequiredProjectSpace(project);
                    }
                }
            }
        }

        return requiredSpace;
    }

    /**
     * Returns the value of the SpaceLeftProperty.getRequiredSpace method. If the property is not set, 0 is returned.
     * @param project the project containing SpaceLeftProperty.getRequiredSpace
     * @return Returns the value of the SpaceLeftProperty.getRequiredSpace method
     */
    @SuppressWarnings("unchecked")
    public long getRequiredProjectSpace(AbstractProject project) {
        SpaceLeftProperty spaceLeftProperty = (SpaceLeftProperty) project.getProperty(SpaceLeftProperty.class);

        if(spaceLeftProperty != null) {
            return spaceLeftProperty.getRequiredSpace();
        }
        return 0L;
    }


}
