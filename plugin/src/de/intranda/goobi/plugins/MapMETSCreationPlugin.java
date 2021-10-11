package de.intranda.goobi.plugins;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import net.xeoh.plugins.base.annotations.PluginImplementation;

import org.apache.log4j.Logger;
import org.goobi.beans.Process;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.importer.DocstructElement;
import org.goobi.production.plugin.interfaces.IPlugin;
import org.goobi.production.plugin.interfaces.IStepPlugin;

import de.sub.goobi.helper.Helper;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import ugh.dl.ContentFile;
import ugh.dl.DigitalDocument;
import ugh.dl.DocStruct;
import ugh.dl.DocStructType;
import ugh.dl.Fileformat;
import ugh.dl.Metadata;
import ugh.dl.MetadataType;
import ugh.dl.Prefs;
import ugh.dl.Reference;
import ugh.exceptions.MetadataTypeNotAllowedException;
import ugh.exceptions.PreferencesException;
import ugh.exceptions.ReadException;
import ugh.exceptions.TypeNotAllowedAsChildException;
import ugh.exceptions.WriteException;

@PluginImplementation
public class MapMETSCreationPlugin implements IStepPlugin, IPlugin {

    private static final String PLUGIN_NAME = "MapMETSCreation";
    private static final Logger logger = Logger.getLogger(MapMETSCreationPlugin.class);

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
            DocStruct physicalElement = dd.getPhysicalDocStruct();
            try {
                createPagination(physicalElement, rootElement, dd);
            } catch (Exception e) {
                logger.error(e);
            }
            MetadataType mdt = prefs.getMetadataTypeByName("singleDigCollection");
            List<? extends Metadata> mdList = rootElement.getAllMetadataByType(mdt);
            if (mdList == null || mdList.isEmpty()) {

                Metadata collection = new Metadata(mdt);
                collection.setValue("Karten");
                rootElement.addMetadata(collection);
            }
        } catch (PreferencesException | MetadataTypeNotAllowedException e) {
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

    public void createPagination(DocStruct physicaldocstruct, DocStruct log, DigitalDocument dd) throws Exception {

        MetadataType MDTypeForPath = prefs.getMetadataTypeByName("pathimagefiles");

        /*-------------------------------- 
         * der physische Baum wird nur
         * angelegt, wenn er noch nicht existierte
         * --------------------------------*/
        if (physicaldocstruct == null) {
            DocStructType dst = prefs.getDocStrctTypeByName("BoundBook");
            physicaldocstruct = dd.createDocStruct(dst);
            dd.setPhysicalDocStruct(physicaldocstruct);
        }

        // check for valid filepath
        try {
            List<? extends Metadata> filepath = physicaldocstruct.getAllMetadataByType(MDTypeForPath);
            if (filepath == null || filepath.isEmpty()) {
                Metadata mdForPath = new Metadata(MDTypeForPath);

                mdForPath.setValue("file://" + process.getImagesTifDirectory(false));

                physicaldocstruct.addMetadata(mdForPath);
            }
        } catch (Exception e) {
            logger.error(e);
        }

        /*------------------------------- 
         * retrieve existing pages/images
         * -------------------------------*/
        DocStructType newPage = prefs.getDocStrctTypeByName("page");

        if (physicaldocstruct.getAllChildren() == null || physicaldocstruct.getAllChildren().isEmpty()) {
            DocStruct dsPage = dd.createDocStruct(newPage);
            try {
                // physical page no
                physicaldocstruct.addChild(dsPage);
                MetadataType mdt = prefs.getMetadataTypeByName("physPageNumber");
                Metadata mdTemp = new Metadata(mdt);
                mdTemp.setValue(String.valueOf(1));
                dsPage.addMetadata(mdTemp);

                // logical page no
                mdt = prefs.getMetadataTypeByName("logicalPageNumber");
                mdTemp = new Metadata(mdt);

                mdTemp.setValue("uncounted");

                dsPage.addMetadata(mdTemp);
                log.addReferenceTo(dsPage, "logical_physical");

                // image name
                ContentFile cf = new ContentFile();

                cf.setLocation("file://" + process.getImagesTifDirectory(false) + process.getTitel().toLowerCase() + ".tif");

                dsPage.addContentFile(cf);

            } catch (TypeNotAllowedAsChildException e) {
                logger.error(e);
            } catch (MetadataTypeNotAllowedException e) {
                logger.error(e);
            }
        } else {
            DocStruct dsPage = physicaldocstruct.getAllChildren().get(0);
            List<Reference> refList = dsPage.getAllFromReferences();
            if (refList == null || refList.isEmpty()) {
                log.addReferenceTo(dsPage, "logical_physical");
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
