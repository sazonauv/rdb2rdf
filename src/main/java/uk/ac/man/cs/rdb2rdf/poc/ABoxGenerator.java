package uk.ac.man.cs.rdb2rdf.poc;

import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;

import java.util.List;

public class ABoxGenerator {

    public static void main(String[] args) {

        int condNumber = 10;
        int drugNumber = 10;
        int patientNumber = 100;

        List<OWLClass> conditionCls = generateConditionClasses(condNumber, "D");
        List<OWLClass> drugCls = generateDrugClasses(drugNumber, "M");
        List<OWLNamedIndividual> drugInds = generateDrugIndividuals(drugNumber, "m");

        List<OWLNamedIndividual> patientInds = generatePatientIndividuals(patientNumber, "p");

        OWLObjectProperty prop;



    }

    private static List<OWLNamedIndividual> generatePatientIndividuals(int patientNumber, String p) {
        return null;
    }

    private static List<OWLNamedIndividual> generateDrugIndividuals(int drugNumber, String m) {
        return null;
    }

    private static List<OWLClass> generateDrugClasses(int drugNumber, String m) {
        return null;
    }

    private static List<OWLClass> generateConditionClasses(int condNumber, String d) {
        return null;
    }


}
