/* ----------------------------------------------------------------------------
 * Copyright (C) 2021      European Space Agency
 *                         European Space Operations Centre
 *                         Darmstadt
 *                         Germany
 * ----------------------------------------------------------------------------
 * System                : ESA NanoSat MO Framework
 * ----------------------------------------------------------------------------
 * Licensed under European Space Agency Public License (ESA-PL) Weak Copyleft – v2.4
 * You may not use this file except in compliance with the License.
 *
 * Except as expressly set forth in this License, the Software is provided to
 * You on an "as is" basis and without warranties of any kind, including without
 * limitation merchantability, fitness for a particular purpose, absence of
 * defects or errors, accuracy or non-infringement of intellectual property rights.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ----------------------------------------------------------------------------
 */
package esa.mo.nmf.ctt.services.mp.pds;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.ccsds.moims.mo.com.archive.consumer.ArchiveAdapter;
import org.ccsds.moims.mo.com.archive.structures.ArchiveDetails;
import org.ccsds.moims.mo.com.archive.structures.ArchiveDetailsList;
import org.ccsds.moims.mo.com.archive.structures.ArchiveQuery;
import org.ccsds.moims.mo.com.archive.structures.ArchiveQueryList;
import org.ccsds.moims.mo.com.structures.ObjectType;
import org.ccsds.moims.mo.mal.MALException;
import org.ccsds.moims.mo.mal.MALInteractionException;
import org.ccsds.moims.mo.mal.structures.ElementList;
import org.ccsds.moims.mo.mal.structures.FineTime;
import org.ccsds.moims.mo.mal.structures.Identifier;
import org.ccsds.moims.mo.mal.structures.IdentifierList;
import org.ccsds.moims.mo.mal.structures.Time;
import org.ccsds.moims.mo.mal.transport.MALMessageHeader;
import org.ccsds.moims.mo.mp.plandistribution.PlanDistributionHelper;
import org.ccsds.moims.mo.mp.structures.PlanVersionDetails;
import esa.mo.com.impl.consumer.ArchiveConsumerServiceImpl;
import esa.mo.com.impl.provider.ArchivePersistenceObject;
import esa.mo.com.impl.util.HelperArchive;
import esa.mo.helpertools.helpers.HelperTime;
import esa.mo.mp.impl.consumer.PlanDistributionConsumerServiceImpl;
import esa.mo.nmf.ctt.services.mp.PublishedUpdatesPanel;
import esa.mo.nmf.ctt.services.mp.util.TimeConverter;

public class PublishedPlansPanel extends PublishedUpdatesPanel {
    private static final Logger LOGGER = Logger.getLogger(PublishedPlansPanel.class.getName());

    private final ArchiveConsumerServiceImpl archiveService;
    private final PlanDistributionConsumerServiceImpl pdsService;
    private final PublishedPlansTable publishedPlansTable;

    public PublishedPlansPanel(ArchiveConsumerServiceImpl archiveService, PlanDistributionConsumerServiceImpl pdsService) {
        super();

        this.archiveService = archiveService;
        this.pdsService = pdsService;

        this.publishedPlansTable = new PublishedPlansTable(archiveService, pdsService);
        try {
          this.publishedPlansTable.monitorPlans();
        } catch (MALInteractionException | MALException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }

        setView(this.publishedPlansTable);

        getRefreshButton().addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                refreshButtonActionPerformed(evt);
            }
        });
    }

    private void refreshButtonActionPerformed(java.awt.event.ActionEvent evt) {

        this.publishedPlansTable.removeAllEntries();

        ObjectType instanceObjectType = PlanDistributionHelper.PLANVERSION_OBJECT_TYPE;

        FineTime startTime = TimeConverter.convert(getRefreshTime());

        if (startTime == null) {
            JOptionPane.showMessageDialog(null, "Please insert date using format: yyyy-MM-dd HH:mm:ss UTC" , "Unparseable date", JOptionPane.PLAIN_MESSAGE);
        }

        ArchiveQueryList archiveQueryList = new ArchiveQueryList();
        ArchiveQuery archiveQuery = new ArchiveQuery();

        archiveQuery.setDomain(null);
        archiveQuery.setNetwork(null);
        archiveQuery.setProvider(null);
        archiveQuery.setRelated(new Long(0));
        archiveQuery.setSource(null);
        archiveQuery.setStartTime(startTime);
        archiveQuery.setEndTime(null);
        archiveQuery.setSortFieldName(null);
        archiveQuery.setSortFieldName(null);

        archiveQueryList.add(archiveQuery);
        try {
            archiveService.getArchiveStub().query(true, instanceObjectType, archiveQueryList, null, new ArchiveAdapter() {
                @Override
                public void queryUpdateReceived(MALMessageHeader msgHeader, ObjectType objType, IdentifierList domain, ArchiveDetailsList objDetails, ElementList objBodies, Map qosProperties) {
                    addEntries(domain, objDetails, objBodies);
                }
                @Override
                public void queryResponseReceived(MALMessageHeader msgHeader, ObjectType objType, IdentifierList domain, ArchiveDetailsList objDetails, ElementList objBodies, Map qosProperties) {
                    addEntries(domain, objDetails, objBodies);
                }
          });
        } catch (MALInteractionException | MALException e) {
            LOGGER.log(Level.SEVERE, null, e);
        }
    }

    private void addEntries(IdentifierList domain, ArchiveDetailsList objDetails, ElementList objBodies) {
        if (objDetails == null) return;
        for (int index = 0; index < objDetails.size(); index++) {
            ArchiveDetails details = objDetails.get(index);
            PlanVersionDetails planVersion = (PlanVersionDetails) objBodies.get(index);

            Time timestamp = HelperTime.fineTimeToTime(details.getTimestamp());
            Long instanceId = details.getDetails().getRelated();
            Long identityId = getIdentityId(domain, instanceId);
            Identifier identity = getIdentity(domain, identityId);

            publishedPlansTable.addEntry(domain, timestamp, identityId, identity, instanceId, planVersion);
        }
    }

    private Long getIdentityId(IdentifierList domain, Long instanceId) {
        Long identityId = null;

        ObjectType instanceObjectType = PlanDistributionHelper.PLANVERSION_OBJECT_TYPE;

        ArchivePersistenceObject instanceObject = HelperArchive
            .getArchiveCOMObject(this.archiveService.getArchiveStub(), instanceObjectType, domain, instanceId);

        if (instanceObject == null) {
            return identityId;
        }

        return instanceObject.getArchiveDetails().getDetails().getRelated();
    }

    private Identifier getIdentity(IdentifierList domain, Long identityId) {
        Identifier identity = null;

        if (identityId == null) {
            return identity;
        }

        ObjectType identityObjectType = PlanDistributionHelper.PLANIDENTITY_OBJECT_TYPE;

        ArchivePersistenceObject identityObject = HelperArchive
            .getArchiveCOMObject(this.archiveService.getArchiveStub(), identityObjectType, domain, identityId);

        if (identityObject == null) {
            return identity;
        }

        return (Identifier) identityObject.getObject();
    }
}
