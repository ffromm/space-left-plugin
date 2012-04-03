package hudson.plugins.space_left;

import hudson.Extension;
import hudson.Util;
import hudson.model.Job;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import java.util.logging.Level;
import java.util.logging.Logger;

//(basically nothing to see here)

/**
 * This Property sets DiskUsage action.
 *
 * @author dvrzalik
 */
public class SpaceLeftProperty extends JobProperty<Job<?, ?>> {
    
    private static final Logger LOGGER = Logger.getLogger(SpaceLeftProperty.class.getName());

    private boolean useRequiredSpace;

    private long requiredSpace;

    private double factor;

    public static final String HAS_REQUIRED_SPACE_KEY = "useRequiredSpace";

    public static final String REQUIRED_SPACE_KEY = "requiredSpace";

    public static final String FACTOR_KEY = "factor";

    /**
     * default factor for required disk space
     */
    private static final double DEFAULT_FACTOR = 1.0;

    public boolean isUseRequiredSpace() {
        return useRequiredSpace;
    }

    public void setUseRequiredSpace(boolean useRequiredSpace) {
        this.useRequiredSpace = useRequiredSpace;
    }

    public long getRequiredSpace() {
        return requiredSpace;
    }

    public void setRequiredSpace(long requiredSpace) {
        this.requiredSpace = requiredSpace;
    }

    public double getFactor() {
        return this.factor >= 1.0 ? this.factor : DEFAULT_FACTOR;
    }

    public void setFactor(double factor) {
        this.factor = factor;
    }

    public long getSpaceNeeded() {
        return (long) (this.getRequiredSpace() * this.getFactor());
    }

    @Extension
    public static final class SpaceLeftDescriptor extends JobPropertyDescriptor {

        public SpaceLeftDescriptor() {
            load();
        }

        @Override
        public String getDisplayName() {
            return Messages.DisplayName();
        }

        @Override
        public SpaceLeftProperty newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            SpaceLeftProperty spaceLeftProperty = new SpaceLeftProperty();

            if(formData.containsKey(HAS_REQUIRED_SPACE_KEY)) {
                try {
                    spaceLeftProperty.setUseRequiredSpace(true);
                    spaceLeftProperty.setRequiredSpace(formData.getJSONObject(HAS_REQUIRED_SPACE_KEY).getLong(REQUIRED_SPACE_KEY));
                    spaceLeftProperty.setFactor(formData.getJSONObject(HAS_REQUIRED_SPACE_KEY).getDouble(FACTOR_KEY));
                } catch(JSONException e) {
                    LOGGER.log(Level.WARNING, "could not get required space from " + formData.getString(REQUIRED_SPACE_KEY));
                }
            }

            return spaceLeftProperty;
        }

        @Override
        public boolean isApplicable(Class<? extends Job> jobType) {
            return true;
        }
    }
}

    
