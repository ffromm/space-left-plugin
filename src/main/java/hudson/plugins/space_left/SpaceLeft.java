package hudson.plugins.space_left;

import hudson.FilePath;
import hudson.model.*;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents an object that knows, how much space is left to execute a job
 *
 * @author ffromm, Frederik Fromm
 */
public class SpaceLeft {
    /**
     * the Logger.
     */
    private static final Logger LOG = Logger.getLogger(SpaceLeft.class.getName());

    /**
     * Returns the free disk space
     *
     * @param slave          slave to get the free space from
     * @param currentProject current project that may have passed custom set space needed
     * @param spaceNeeded    custom set space needed by jop parameter REQUIRED_SPACE
     * @return the free disk space
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    public Long getFreeSpace(Slave slave, AbstractProject<FreeStyleProject, FreeStyleBuild> currentProject, long spaceNeeded) throws IOException, InterruptedException {
        //LOG.log(Level.INFO, "getting freeSpaceOnSlave for " + currentProject.getName() + " on " + slave);
        FilePath p = slave.getRootPath();

        if (p == null) {
            return 0L;
        }

        Long freeSpaceOnSlave = p.act(new GetUsableSpace());

        //LOG.log(Level.INFO, "freeSpaceOnSlave init: " + freeSpaceOnSlave);

        if (spaceNeeded > 0) {
            freeSpaceOnSlave -= spaceNeeded;
        } else if (currentProject != null) {
            freeSpaceOnSlave -= this.getRequiredSpace(currentProject);
        }

        //LOG.log(Level.INFO, "freeSpaceOnSlave start: " + freeSpaceOnSlave);

        FilePath workspace = p.child("workspace");

        if (workspace.exists()) {
            for (FilePath projectDir : workspace.listDirectories()) {
                //LOG.log(Level.INFO, "checking dir " + projectDir);
                TopLevelItem topLevelItem = Jenkins.getInstance().getItem(projectDir.getName());

                if (topLevelItem instanceof AbstractProject) {
                    AbstractProject project = (AbstractProject) topLevelItem;
                    //LOG.log(Level.INFO, "project: " + project.getName());
                    Long requiredSpace = this.getRequiredSpace(project);
                    freeSpaceOnSlave -= requiredSpace;

                    //LOG.log(Level.INFO, "freeSpaceOnSlave update: " + freeSpaceOnSlave);

                    if (freeSpaceOnSlave <= 0) {
                        freeSpaceOnSlave = 0L;
                        break;
                    }
                }
            }
        }

        //LOG.log(Level.INFO, "freeSpaceOnSlave final: " + freeSpaceOnSlave);

        return freeSpaceOnSlave;
    }

    /**
     * Returns the value of the SpaceLeftProperty.getRequiredSpace method. If the property is not set, 0 is returned.
     * @param project the project containing SpaceLeftProperty.getRequiredSpace
     * @return Returns the value of the SpaceLeftProperty.getRequiredSpace method
     */
    public Long getRequiredSpace(AbstractProject project) {
        SpaceLeftProperty spaceLeftProperty = (SpaceLeftProperty) project.getProperty(SpaceLeftProperty.class);

        if(spaceLeftProperty != null) {
            return spaceLeftProperty.getRequiredSpace();
        }
        return 0L;
    }

    /**
     * inner class that is executed on slave to get free disk space
     */
    protected static final class GetUsableSpace implements FilePath.FileCallable<Long> {
        @IgnoreJRERequirement
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            try {
                long s = f.getUsableSpace();

                if (s <= 0) {
                    return null;
                }

                return s;
            } catch (LinkageError e) {
                // pre-mustang
                return null;
            }
        }

        private static final long serialVersionUID = 1L;
    }
}
