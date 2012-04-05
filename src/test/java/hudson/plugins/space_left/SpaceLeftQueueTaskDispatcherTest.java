package hudson.plugins.space_left;

import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.CauseOfBlockage;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import org.junit.Test;
import org.jvnet.hudson.test.HudsonTestCase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by IntelliJ IDEA.
 * User: jet
 * Date: 3/6/12
 * Time: 10:42 AM
 * To change this template use File | Settings | File Templates.
 */
public class SpaceLeftQueueTaskDispatcherTest extends HudsonTestCase {
    @Test
    public void testGetFreeSpace() throws Exception {

        // init slave
        LabelAtom label = new LabelAtom("label");
        DumbSlave slave = this.createSlave(label);
        SlaveComputer c = slave.getComputer();
        c.connect(false).get(); // wait until it's connected
        if(c.isOffline()) {
            fail("Slave failed to go online: "+c.getLog());
        }

        // get free space from empty slave
        SpaceLeftQueueTaskDispatcher spaceLeftQueueTaskDispatcher = new SpaceLeftQueueTaskDispatcher();

        Long freeSpace = spaceLeftQueueTaskDispatcher.getFreeSpace(slave, null, -1L);

        assertTrue(freeSpace > 0L);

        // add project to slave
        FreeStyleProject project = this.createFreeStyleProject();
        project.setAssignedLabel(label);

        Queue.BuildableItem item = new Queue.BuildableItem(new Queue.WaitingItem(Calendar.getInstance(), project, new ArrayList<Action>()));

        CauseOfBlockage causeOfBlockage = spaceLeftQueueTaskDispatcher.canTake(slave, item);

        assertNull(causeOfBlockage);

        // set required space property on project, that needs lots of space
        SpaceLeftProperty spaceLeftProperty = new SpaceLeftProperty();
        spaceLeftProperty.setRequiredSpace(Long.MAX_VALUE / 4L);
        
        project.addProperty(spaceLeftProperty);

        causeOfBlockage = spaceLeftQueueTaskDispatcher.canTake(slave, item);
        
        assertNotNull(causeOfBlockage);

        // reset required space property to 1 MB
        spaceLeftProperty.setRequiredSpace(1000000L);

        causeOfBlockage = spaceLeftQueueTaskDispatcher.canTake(slave, item);

        assertNull(causeOfBlockage);

        Long otherFreeSpace = spaceLeftQueueTaskDispatcher.getFreeSpace(slave, project, -1L);

        //assertEquals(freeSpace/100000L - 10L, otherFreeSpace/100000L);
        assertTrue((Math.abs(otherFreeSpace - (freeSpace - 1000000L)) < 100000L) );

        // take required space of other projects on slave into count
        FreeStyleProject otherProject = this.createFreeStyleProject();
        otherProject.setAssignedLabel(label);
        SpaceLeftProperty otherSpaceLeftProperty = new SpaceLeftProperty();
        otherSpaceLeftProperty.setRequiredSpace(2000000L);
        otherProject.addProperty(otherSpaceLeftProperty);
        FreeStyleBuild b = otherProject.scheduleBuild2(0).get();

        assertTrue(b.getWorkspace().exists());

        otherFreeSpace = spaceLeftQueueTaskDispatcher.getFreeSpace(slave, project, -1L);

        //assertEquals(freeSpace/100000L - 30L, otherFreeSpace / 100000L);
        assertTrue((Math.abs(otherFreeSpace - (freeSpace - 3000000L)) < 100000L) );

        StringParameterValue value = new StringParameterValue("REQUIRED_SPACE", "3000000");
        List<ParameterValue> params = new ArrayList<ParameterValue>();
        params.add(value);
        item.addAction(new ParametersAction(params));

        causeOfBlockage = spaceLeftQueueTaskDispatcher.canTake(slave, item);

        assertNull(causeOfBlockage);

        otherFreeSpace = spaceLeftQueueTaskDispatcher.getFreeSpace(slave, project, 3000000L);

        //assertEquals(freeSpace/100000L - 50L, otherFreeSpace / 100000L);

        assertTrue((Math.abs(otherFreeSpace - (freeSpace - 5000000L)) < 100000L) );
    }

}
