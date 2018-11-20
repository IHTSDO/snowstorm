package org.snomed.snowstorm.fhir.services;

import org.junit.Assert;
import org.junit.Test;
import org.snomed.snowstorm.fhir.config.FHIRConstants;
import org.hl7.fhir.dstu3.model.StringType;

public class FHIRHelperTest {

    @Test
    public void getBranchForDefaultVersionTest() throws FHIROperationException{
        FHIRHelper helper = new FHIRHelper();
            StringType versionStr = new StringType(FHIRConstants.SNOMED_URI);
            Assert.assertEquals(helper.getBranchForVersion(versionStr), "MAIN");
    }

    @Test
    public void getBranchForVersionTest() throws FHIROperationException{
        FHIRHelper helper = new FHIRHelper();
        for(FHIRConstants.SnomedEdition s : FHIRConstants.SnomedEdition.values()) {
            StringType versionStr = new StringType(s.uri());
            Assert.assertEquals(helper.getBranchForVersion(versionStr), "MAIN/" + s.sctid());
        }
    }

    @Test
    public void getBranchForVersionWithDateTest() throws FHIROperationException{
        String versionTimestamp = "20180101";
        FHIRHelper helper = new FHIRHelper();
        for(FHIRConstants.SnomedEdition s : FHIRConstants.SnomedEdition.values()) {
            StringType versionStr = new StringType(FHIRConstants.SNOMED_URI
                    + "/" + s.sctid() + "/"
                    + FHIRConstants.VERSION + "/"
                    + versionTimestamp);
            Assert.assertEquals(helper.getBranchForVersion(versionStr), "MAIN/" + s.sctid() + "/" + versionTimestamp);
        }
    }

    @Test
    public void getBranchForVersionWithIllegalDateRangeTest(){
        String versionTimestamp = "19180101";
        FHIRHelper helper = new FHIRHelper();
        for(FHIRConstants.SnomedEdition s : FHIRConstants.SnomedEdition.values()) {
            StringType versionStr = new StringType(FHIRConstants.SNOMED_URI
                    + "/" + s.sctid() + "/"
                    + FHIRConstants.VERSION + "/"
                    + versionTimestamp);
            try{
                helper.getBranchForVersion(versionStr);
                Assert.fail();
            }
            catch(FHIROperationException e){
                System.out.println("Test should generate FHIROperationException! " + e.getMessage());
            }
        }
    }

    @Test
    public void getBranchForVersionWithIllegalDateTest(){
        String versionTimestamp = "abcd";
        FHIRHelper helper = new FHIRHelper();
        for(FHIRConstants.SnomedEdition s : FHIRConstants.SnomedEdition.values()) {
            StringType versionStr = new StringType(FHIRConstants.SNOMED_URI
                    + "/" + s.sctid() + "/"
                    + FHIRConstants.VERSION + "/"
                    + versionTimestamp);
            try{
                helper.getBranchForVersion(versionStr);
                Assert.fail();
            }
            catch(FHIROperationException e){
                System.out.println("Test should generate FHIROperationException! " + e.getMessage());
            }
        }
    }

    @Test
    public void getSnomedEditionTest(){
        FHIRHelper helper = new FHIRHelper();
        for(FHIRConstants.SnomedEdition s : FHIRConstants.SnomedEdition.values()) {
            StringType versionStr = new StringType(FHIRConstants.SNOMED_URI
                    + "/" + s.sctid());
            Assert.assertEquals(s, helper.getSnomedEdition(versionStr));
        }
    }

    @Test
    public void getSnomedEditionLanguageCodeTest(){
        FHIRConstants.SnomedEdition s = FHIRConstants.SnomedEdition.lookup("45991000052106") ;
        Assert.assertEquals(s.languageCode(), "sv");

    }

    @Test
    public void lookupIllegalSnomedEditionCodeTest(){
        FHIRConstants.SnomedEdition s = FHIRConstants.SnomedEdition.lookup("123") ;
        Assert.assertEquals(s, FHIRConstants.SnomedEdition.INTERNATIONAL);
    }
}
