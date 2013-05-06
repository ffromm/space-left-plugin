package hudson.plugins.space_left;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
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
    private static final Logger LOG = Logger.getLogger(SpaceLeftQueueTaskDispatcher.class.getName());

    /**
     * Name of the job parameter to inject required space. May be used for generic jobs that
     * are triggered by preceding jobs.
     */
    private static final String REQUIRED_SPACE_PARAMETER = "REQUIRED_SPACE";

    /**
     * Returns null, if the given item can be build on the given node. This dispatcher checks, if there is enough disk space
     * on the node. It must be greater than 2 times the last measured build of the item.
     *
     * @param node the slave connected to the jenkins master
     * @param item the job to be executed
     * @return null, if the given item can be build on the given node
     */
    @SuppressWarnings("unchecked")
    @Override
    public CauseOfBlockage canTake(Node node, Queue.BuildableItem item) {
        // if not on slave, continue as master should have enough disk space
        if (!(node instanceof Slave)) {
            return super.canTake(node, item);
        }

        // Get space needed from string parameter action
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

        // Get required space from node
        try {
            RequiredSpace requiredSpace = new RequiredSpace(node);

            // if space needed is given by parameter, the current build required space is overridden.
            // removing it from workspace analysis
            AbstractProject currentProject = null;

            if (spaceNeeded > -1L && item.task instanceof AbstractProject) {
                currentProject = (AbstractProject) item.task;
            }

            spaceNeeded += requiredSpace.getRequiredSpace(currentProject);

            FilePath p = node.getRootPath();

            if (p != null) {
                Long freeSpaceOnSlave = p.act(new GetUsableSpace());

                if (freeSpaceOnSlave - spaceNeeded <= 0L) {
                    LOG.log(Level.WARNING, "slave " + node.getNodeName() + " has not enough free disk space!");
                    return CauseOfBlockage.fromMessage(Messages._NotEnoughFreeDiskSpaceOnSlave());
                }
            }

        } catch (IOException e) {
            LOG.log(Level.SEVERE, e.getMessage());
        } catch (InterruptedException e) {
            LOG.log(Level.SEVERE, e.getMessage());
        }

        return super.canTake(node, item);
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

        private static final long serialVersionUID = 123L;
    }
}
