package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.log4j.Logger;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IStepPlugin;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import de.sub.goobi.metadaten.MetaPerson;
import de.sub.goobi.metadaten.MetadataGroupImpl;
import de.sub.goobi.metadaten.Metadaten;
import de.sub.goobi.metadaten.MetadatenHelper;
import de.sub.goobi.metadaten.MetadatumImpl;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataGroup;
import ugh.dl.Person;
import ugh.dl.Prefs;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.WriteException;

@PluginImplementation
public class MetsCreationPlugin implements IStepPlugin, IPlugin {

    private static final String PLUGIN_NAME = "MetsCreation";
    private static final Logger logger = Logger.getLogger(MetsCreationPlugin.class);

    private Step step;
    private String returnPath;
    private Process process;
    private Prefs prefs;

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String getTitle() {
        return PLUGIN_NAME;
    }

    
    public String getDescription() {
        return PLUGIN_NAME;
    }

    @Override
    public void initialize(Step step, String returnPath) {
        this.step = step;
        this.returnPath = returnPath;
        process = step.getProzess();
        prefs = process.getRegelsatz().getPreferences();
    }

    @Override
    public boolean execute() {
        Fileformat ff = null;
        try {
            ff = process.readMetadataFile();
        } catch (ReadException | PreferencesException | SwapException | DAOException | WriteException | IOException | InterruptedException e) {
            logger.error(e);
            Helper.setFehlerMeldung(e);
            return false;
        }

        try {
            DigitalDocument dd = ff.getDigitalDocument();
            DocStruct rootElement = dd.getLogicalDocStruct();
            MetadatenHelper metadatenHelper = new MetadatenHelper(prefs, dd);
            createDefaultValues(metadatenHelper, rootElement);

        } catch (PreferencesException e) {
            logger.error(e);
            Helper.setFehlerMeldung(e);
            return false;
        }

        try {
            process.writeMetadataFile(ff);
        } catch (PreferencesException | SwapException | DAOException | WriteException | IOException | InterruptedException e) {
            Helper.setFehlerMeldung(e);
            return false;
        }
        return true;
    }

    private void createDefaultValues(MetadatenHelper metadatenHelper, DocStruct element) {

        LinkedList<MetadatumImpl> lsMeta = new LinkedList<MetadatumImpl>();
        LinkedList<MetaPerson> lsPers = new LinkedList<MetaPerson>();
        List<MetadataGroupImpl> metaGroups = new LinkedList<MetadataGroupImpl>();
        /*
         * -------------------------------- alle Metadaten und die DefaultDisplay-Werte anzeigen --------------------------------
         */
        List<? extends Metadata> myTempMetadata =
                metadatenHelper.getMetadataInclDefaultDisplay(element, (String) Helper
                        .getManagedBeanValue("#{LoginForm.myBenutzer.metadatenSprache}"), false, process);
        if (myTempMetadata != null) {
            for (Metadata metadata : myTempMetadata) {
                MetadatumImpl meta = new MetadatumImpl(metadata, 0, this.prefs, this.process);
                meta.getSelectedItem();
                lsMeta.add(meta);
            }
        }

        /*
         * -------------------------------- alle Personen und die DefaultDisplay-Werte ermitteln --------------------------------
         */
        myTempMetadata =
                metadatenHelper.getMetadataInclDefaultDisplay(element, (String) Helper
                        .getManagedBeanValue("#{LoginForm.myBenutzer.metadatenSprache}"), true, this.process);
        if (myTempMetadata != null) {
            for (Metadata metadata : myTempMetadata) {
                lsPers.add(new MetaPerson((Person) metadata, 0, this.prefs, element));
            }
        }

        List<MetadataGroup> groups =
                metadatenHelper.getMetadataGroupsInclDefaultDisplay(element, (String) Helper
                        .getManagedBeanValue("#{LoginForm.myBenutzer.metadatenSprache}"), this.process);
        if (groups != null) {
            for (MetadataGroup mg : groups) {
                metaGroups.add(new MetadataGroupImpl(prefs, process, mg));
            }
        }

        if (element.getAllChildren() != null && element.getAllChildren().size() > 0) {
            for (DocStruct ds : element.getAllChildren()) {
                createDefaultValues(metadatenHelper, ds);
            }
        }

    }

    @Override
    public String cancel() {
        return returnPath;
    }

    @Override
    public String finish() {
        return returnPath;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public Step getStep() {
        return step;
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return null;
    }

}
