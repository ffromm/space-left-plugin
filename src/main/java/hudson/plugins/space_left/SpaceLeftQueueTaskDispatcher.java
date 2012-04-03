package hudson.plugins.space_left;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.CauseOfBlockage;
import hudson.model.queue.QueueTaskDispatcher;
import hudson.remoting.VirtualChannel;
import org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This QueueTaskDispatcher returns a CauseOfBlockage if the node that should be used to execute the item on
 * has not enough free disk space. It must have two times of the last measured workspace size of the item.
 *
 * @author ffromm, Frederik Fromm
 */
@Extension
public class SpaceLeftQueueTaskDispatcher extends QueueTaskDispatcher {
    /**
     * the logger
     */
    private static final Logger LOG = Logger.getLogger(hudson.plugins.space_left.SpaceLeftQueueTaskDispatcher.class.getName());

    private static final String REQUIRED_SPACE_PARAMETER = "REQUIRED_SPACE";

    /**
     * Returns null, if the given item can be build on the given node. This dispatcher checks, if there is enough disk space
     * on the node. It must be greater than 2 times the last measured build of the item.
     *
     * @param node the slave connected to the jenkins master
     * @param item the job to be executed
     * @return null, if the given item can be build on the given node
     */
    @Override
    public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {

        long spaceNeeded = -1L;

        ParametersAction parametersAction = item.getAction(ParametersAction.class);

        if (parametersAction != null) {
            ParameterValue parameterValue = parametersAction.getParameter(REQUIRED_SPACE_PARAMETER);
            if (parameterValue instanceof StringParameterValue) {
                String value = ((StringParameterValue) parameterValue).value;

                try {
                    spaceNeeded = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    LOG.log(Level.WARNING, "Error parsing required space from " + value);
                }

            }
        }

        AbstractProject currentProject = null;

        if (spaceNeeded == -1 && item.task instanceof AbstractProject) {
            currentProject = (AbstractProject) item.task;
        }

        Long freeDiskSpace = -1L;

        if (node instanceof Slave) {
            Slave slave = (Slave) node;

            LOG.log(Level.INFO, "checking disk usage on slave: " + slave.getNodeName());

            try {
                freeDiskSpace = this.getFreeSpace(slave, currentProject, spaceNeeded);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, e.getMessage());
            } catch (InterruptedException e) {
                LOG.log(Level.SEVERE, e.getMessage());
            }
        }

        if (freeDiskSpace <= 0) {
            LOG.log(Level.WARNING, "slave " + node.getNodeName() + " has not enough free disk space!");
            return CauseOfBlockage.fromMessage(Messages._NotEnoughFreeDiskSpaceOnSlave());
        }

        LOG.log(Level.INFO, "detected enough free diskspace for job, continue...");

        return super.canTake(node, item);
    }

    /**
     * Returns the free disk space
     *
     * @param slave
     * @param currentProject
     * @param spaceNeeded    @return the free disk space
     * @throws IOException
     * @throws InterruptedException
     */
    protected Long getFreeSpace(Slave slave, AbstractProject currentProject, long spaceNeeded) throws IOException, InterruptedException {
        FilePath p = slave.getRootPath();

        if (p == null) {
            return null;
        }

        Long freeSpaceOnSlave = p.act(new GetUsableSpace());

        for (LabelAtom labelAtom : slave.getAssignedLabels()) {
            for (AbstractProject project : labelAtom.getTiedJobs()) {
                if (project.equals(currentProject) && spaceNeeded > 0L) {
                    freeSpaceOnSlave -= spaceNeeded;
                } else {
                    SpaceLeftProperty spaceLeftProperty = (SpaceLeftProperty) project.getProperty(SpaceLeftProperty.class);

                    if (spaceLeftProperty != null) {
                        freeSpaceOnSlave -= spaceLeftProperty.getRequiredSpace();
                    }
                }

                if (freeSpaceOnSlave <= 0) {
                    freeSpaceOnSlave = 0L;
                    break;
                }
            }

            if(freeSpaceOnSlave <= 0) {
                freeSpaceOnSlave = 0L;
                break;
            }
        }

        return freeSpaceOnSlave;
    }

    /**
     * inner class that is executed on slave to get free disk space
     */
    protected static final class GetUsableSpace implements FilePath.FileCallable<Long> {
        @IgnoreJRERequirement
        public Long invoke(File f, VirtualChannel channel) throws IOException {
            try {
                long s = f.getUsableSpace();
                if (s <= 0) return null;
                return s;
            } catch (LinkageError e) {
                // pre-mustang
                return null;
            }
        }

        private static final long serialVersionUID = 1L;
    }
}
