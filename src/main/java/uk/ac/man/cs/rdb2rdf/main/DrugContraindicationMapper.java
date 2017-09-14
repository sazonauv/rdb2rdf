package uk.ac.man.cs.rdb2rdf.main;

import com.opencsv.CSVReader;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import java.io.*;
import java.util.*;

import static uk.ac.man.cs.rdb2rdf.main.CSV.processCell;
import static uk.ac.man.cs.rdb2rdf.main.CSV2OWLConverter.*;


/**
 * Created by slava on 04/09/17.
 */
public class DrugContraindicationMapper {

    private static final String IRI_NAME = //"http://owl.cs.manchester.ac.uk/contraindication";
                                            "http://mekon/demo.owl";
    private static final String TOP_CONTRAINDICATION = "Contraindication";
    private static final String GIVEN_CONTRAINDICATION = "GivenContraindication";
    private static final String INFERRED_CONTRAINDICATION = "InferredContraindication";


    private OWLOntologyManager manager;
    private OWLOntology ontology;
    private OWLDataFactory factory;
    private OWLReasoner reasoner;

    private Map<String, OWLClass> icd9Map;

    private Map<String, Contraindication> drug2ContrMap;





    public DrugContraindicationMapper(File drug2cond, File cond2ICD, File drug2cat)
            throws OWLOntologyCreationException, IOException {
        // create an ontology
        IRI iri = IRI.create(IRI_NAME);
        manager = OWLManager.createOWLOntologyManager();
        ontology = manager.createOntology(iri);
        factory = manager.getOWLDataFactory();
        init(drug2cond, cond2ICD, drug2cat);
    }

    private void init(File drug2condFile, File cond2ICDFile, File drug2catFile) throws IOException {
        // map conditions to ICD
        Map<String, Set<String>> cond2ICDMap = new HashMap<>();
        CSVReader reader = new CSVReader(new FileReader(cond2ICDFile));
        List<String[]> cond2ICDRows = reader.readAll();
        reader.close();
        cond2ICDRows.remove(0);
        for (String[] row : cond2ICDRows) {
            String condID = processCell(row[2]);
            Set<String> codes = cond2ICDMap.get(condID);
            if (codes == null) {
                codes = new HashSet<>();
                cond2ICDMap.put(condID, codes);
            }
            String code = processCell(row[0]);
            codes.add(code);
        }

        // map drugs to ICD codes
        reader = new CSVReader(new FileReader(drug2condFile));
        List<String[]> drug2condRows = reader.readAll();
        reader.close();
        drug2condRows.remove(0);
        drug2ContrMap = new HashMap<>();
        for (String[] row : drug2condRows) {
            Contraindication contr = new Contraindication();
            contr.drugCode = processCell(row[1]);
            contr.drugName = processCell(row[2]);
            String conditionId = processCell(row[3]);
            if (cond2ICDMap.containsKey(conditionId)) {
                contr.conditions = cond2ICDMap.get(conditionId);
            } else {
                contr.conditions = new HashSet<>();
            }
            String severity = processCell(row[7]);
            if (severity.contains("Minimal")) {
                contr.severity = "Minimal";
            } else if (severity.contains("Moderate")) {
                contr.severity = "Moderate";
            } else if (severity.contains("High")) {
                contr.severity = "High";
            } else {
                contr.severity = severity;
            }
            drug2ContrMap.put(contr.drugCode, contr);
        }

        // map drugs to categories
        reader = new CSVReader(new FileReader(drug2catFile));
        List<String[]> drug2catRows = reader.readAll();
        reader.close();
        drug2catRows.remove(0);
        for (String[] row : drug2catRows) {
            String drugCode = processCell(row[0]);
            Contraindication contr = drug2ContrMap.get(drugCode);
            if (contr != null) {
                contr.drugCategory = processCell(row[3]);
            }
        }

    }


    private void addMekonOntology(File file) throws OWLOntologyCreationException {
        Out.p("Loading Mekpon template");
        OWLOntology mekonOntology = manager.loadOntologyFromOntologyDocument(file);
        manager.addAxioms(ontology, mekonOntology.getAxioms());
    }



    private void addICD9Ontology(File file) throws OWLOntologyCreationException {
        Out.p("Loading ICD9 terminology");
        OWLOntology icd9Ontology = manager.loadOntologyFromOntologyDocument(file);
        // get class hierarchy
        Out.p("Adding class hierarchy");
        Set<OWLSubClassOfAxiom> classAxioms = new HashSet<>();
        for (OWLAxiom axiom : icd9Ontology.getAxioms()) {
            if (axiom instanceof OWLSubClassOfAxiom) {
                OWLSubClassOfAxiom clAxiom = (OWLSubClassOfAxiom) axiom;
                if (!clAxiom.getSuperClass().equals(factory.getOWLThing())) {
                    classAxioms.add(clAxiom);
                }
            }
        }
        manager.addAxioms(ontology, classAxioms);
        Out.p("Class axioms are added: " + classAxioms.size());
        // adding annotations
        addAnnotations(icd9Ontology);
    }


    private void addAnnotations(OWLOntology icd9Ontology) {
        // get annotations
        Out.p("Adding annotations");
        Set<OWLClass> cls = icd9Ontology.getClassesInSignature();
        Set<OWLAnnotationAssertionAxiom> labelAnnots = new HashSet<>();
        for (OWLClass cl : cls) {
            Set<OWLAnnotationAssertionAxiom> annots = icd9Ontology.getAnnotationAssertionAxioms(cl.getIRI());
            for (OWLAnnotationAssertionAxiom annot : annots) {
                if (annot.getProperty().getIRI().toString().contains("prefLabel")) {
                    labelAnnots.add(annot);
                }
            }
        }
        Out.p("Annotations are added: " + labelAnnots.size());
        manager.addAxioms(ontology, labelAnnots);
        // create mapping
        icd9Map = new HashMap<>();
        Out.p("Creating ICD9 code-class mappings");
        for (OWLClass cl : cls) {
            String clCode = cl.getIRI().toString().replace("http://purl.bioontology.org/ontology/ICD9CM/", "");
            icd9Map.put(clCode, cl);
        }
    }


    private void computeClassHierarchy() throws Exception {
        // process the ontology by the reasoner
        Out.p("\nInitialising the reasoner");
        reasoner = ReasonerLoader.initReasoner(ontology);
        reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
    }


    private void createMekonOntology(File ontFile) throws IOException, OWLOntologyStorageException {
        // populate the ontology
        // classes
        IRI topDrugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass topDrugClass = factory.getOWLClass(topDrugClassIRI);
        IRI topCondClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_DISEASE);
        OWLClass topCondClass = factory.getOWLClass(topCondClassIRI);
        IRI topPatientClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_PATIENT);
        OWLClass topPatientClass = factory.getOWLClass(topPatientClassIRI);
        IRI topClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_CLASS);
        OWLClass topClass = factory.getOWLClass(topClassIRI);

        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topDrugClass, topClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topCondClass, topClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPatientClass, topClass));


        IRI topPlausClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_PLAUSIBILITY);
        OWLClass topPlausClass = factory.getOWLClass(topPlausClassIRI);
        IRI lowPlausClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + LOW_PLAUSIBILITY);
        OWLClass lowPlausClass = factory.getOWLClass(lowPlausClassIRI);
        IRI mediumPlausClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + MEDIUM_PLAUSIBILITY);
        OWLClass mediumPlausClass = factory.getOWLClass(mediumPlausClassIRI);
        IRI highPlausClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + HIGH_PLAUSIBILITY);
        OWLClass highPlausClass = factory.getOWLClass(highPlausClassIRI);

        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPlausClass, topClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(lowPlausClass, topPlausClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(mediumPlausClass, lowPlausClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(highPlausClass, mediumPlausClass));



        Set<OWLClass> topCondClasses = reasoner.getSubClasses(
                factory.getOWLThing(), true).getFlattened();
        for (OWLClass cl : topCondClasses) {
            manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(cl, topCondClass));
        }

        // properties
        IRI hasDrugIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasContraindicatedDrug");
        OWLObjectProperty hasDrugProp = factory.getOWLObjectProperty(hasDrugIRI);
        IRI hasConditionIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasCondition");
        OWLObjectProperty hasConditionProp = factory.getOWLObjectProperty(hasConditionIRI);
        IRI hasSeverityIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "hasContraindicationPlausibility");
        OWLObjectProperty hasSeverityProp = factory.getOWLObjectProperty(hasSeverityIRI);
        OWLAnnotationProperty labelProp = factory.getRDFSLabel();


        OWLClassExpression onlyDrugExpr = factory.getOWLObjectAllValuesFrom(hasDrugProp, topDrugClass);
        OWLClassExpression onlyCondExpr = factory.getOWLObjectAllValuesFrom(hasConditionProp, topCondClass);
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPatientClass, onlyDrugExpr));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(topPatientClass, onlyCondExpr));


        for (String drugCode : drug2ContrMap.keySet()) {

            Contraindication contr = drug2ContrMap.get(drugCode);

            // drugName
            IRI drugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + drugCode);
            OWLClass drugClass = factory.getOWLClass(drugClassIRI);

            // drugName code
            manager.addAxiom(ontology,
                    factory.getOWLAnnotationAssertionAxiom(
                            labelProp, drugClass.getIRI(), factory.getOWLLiteral(contr.drugName)));

            // categories
            if (contr.drugCategory != null) {
                IRI drugCategoryIRI = IRI.create(IRI_NAME + IRI_DELIMITER + contr.drugCategory);
                OWLClass drugCategoryClass = factory.getOWLClass(drugCategoryIRI);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(drugCategoryClass, topDrugClass));
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(drugClass, drugCategoryClass));
            }

            // conditions
            Set<String> condIDs = contr.conditions;
            for (String condID : condIDs) {
                OWLClass condClass = findICD9Class(condID);
                if (condClass == null) {
                    continue;
                }

                // patient
                IRI patientIRI = IRI.create(IRI_NAME + IRI_DELIMITER
                        + TOP_PATIENT + ENTITY_DELIMITER + condClass.getIRI().getShortForm());
                OWLClass patientClass = factory.getOWLClass(patientIRI);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(patientClass, topPatientClass));

                OWLClassExpression patientExpr = factory.getOWLObjectIntersectionOf(
                        topPatientClass,
                        factory.getOWLObjectSomeValuesFrom(hasConditionProp, condClass));

                manager.addAxiom(ontology, factory.getOWLEquivalentClassesAxiom(patientClass, patientExpr));

                // implication
                OWLClass plausClass = null;
                if (contr.severity.equals("Minimal")) {
                    plausClass = lowPlausClass;
                } else if (contr.severity.equals("Moderate")) {
                    plausClass = mediumPlausClass;
                } else if (contr.severity.equals("High")) {
                    plausClass = highPlausClass;
                } else {
                    System.out.println("Wrong plausibility : " + contr.severity);
                }

                OWLClassExpression plausExpr = factory.getOWLObjectIntersectionOf(drugClass,
                        factory.getOWLObjectSomeValuesFrom(hasSeverityProp, plausClass));
                OWLClassExpression drugExpr = factory.getOWLObjectSomeValuesFrom(hasDrugProp, plausExpr);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(patientClass, drugExpr));

            }
        }

        // save the ontology
        if (!ontFile.exists()) {
            ontFile.createNewFile();
        }

        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }




    private void createMappingOntology(File ontFile) throws IOException, OWLOntologyStorageException {
        // populate the ontology
        // classes
        IRI topDrugClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_MEDICINE);
        OWLClass topDrugClass = factory.getOWLClass(topDrugClassIRI);
        IRI topContrClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_CONTRAINDICATION);
        OWLClass topContrClass = factory.getOWLClass(topContrClassIRI);
        IRI givenContrClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + GIVEN_CONTRAINDICATION);
        OWLClass givenContrClass = factory.getOWLClass(givenContrClassIRI);
        IRI inferredContrClassIRI = IRI.create(IRI_NAME + IRI_DELIMITER + INFERRED_CONTRAINDICATION);
        OWLClass inferredContrClass = factory.getOWLClass(inferredContrClassIRI);
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(givenContrClass, topContrClass));
        manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(inferredContrClass, topContrClass));

        // properties
        IRI hasDrugIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "drug");
        OWLObjectProperty hasDrugProp = factory.getOWLObjectProperty(hasDrugIRI);
        IRI hasConditionIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "condition");
        OWLObjectProperty hasConditionProp = factory.getOWLObjectProperty(hasConditionIRI);
        IRI hasSeverityIRI = IRI.create(IRI_NAME + IRI_DELIMITER + "plausibility");
        OWLDataProperty hasSeverityProp = factory.getOWLDataProperty(hasSeverityIRI);
        OWLAnnotationProperty labelProp = factory.getRDFSLabel();


        Set<OWLClass> cls1 = new HashSet<>();
        Set<OWLClass> cls2 = new HashSet<>();
        Map<OWLNamedIndividual, OWLClass> contrCondMap = new HashMap<>();
        for (String drugCode : drug2ContrMap.keySet()) {

            Contraindication contr = drug2ContrMap.get(drugCode);

            // drugName
            IRI drugIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER + drugCode);
            OWLNamedIndividual drugInd = factory.getOWLNamedIndividual(drugIndIRI);
            manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(topDrugClass, drugInd));

            // drugName code
            manager.addAxiom(ontology,
                    factory.getOWLAnnotationAssertionAxiom(
                            labelProp, drugInd.getIRI(), factory.getOWLLiteral(contr.drugName)));

            // categories
            if (contr.drugCategory != null) {
                IRI drugCategoryIRI = IRI.create(IRI_NAME + IRI_DELIMITER + contr.drugCategory);
                OWLClass drugCategoryClass = factory.getOWLClass(drugCategoryIRI);
                manager.addAxiom(ontology, factory.getOWLSubClassOfAxiom(drugCategoryClass, topDrugClass));
                manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(drugCategoryClass, drugInd));
            }

            // conditions
            Set<String> condIDs = contr.conditions;
            for (String condID : condIDs) {
                OWLClass condClass = findICD9Class(condID);
                if (condClass == null) {
                    continue;
                }
                cls1.add(condClass);
                Set<OWLClass> condClasses = new HashSet<>(
                        reasoner.getSubClasses(condClass, false).getFlattened()
                );
                condClasses.remove(factory.getOWLNothing());
                condClasses.add(condClass);

                for (OWLClass subCl : condClasses) {
                    cls2.add(subCl);
                    IRI condIndIRI = IRI.create(IRI_NAME + IRI_DELIMITER +
                            subCl.getIRI().getShortForm());
                    OWLNamedIndividual condInd = factory.getOWLNamedIndividual(condIndIRI);

                    // axioms
                    manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(subCl, condInd));

                    IRI contrIRI = IRI.create(IRI_NAME + IRI_DELIMITER +
                            subCl.getIRI().getShortForm() + ENTITY_DELIMITER + drugCode);
                    OWLNamedIndividual contrInd = factory.getOWLNamedIndividual(contrIRI);

                    manager.addAxiom(ontology, factory.getOWLObjectPropertyAssertionAxiom(
                            hasDrugProp, contrInd, drugInd));
                    manager.addAxiom(ontology, factory.getOWLObjectPropertyAssertionAxiom(
                            hasConditionProp, contrInd, condInd));

                    manager.addAxiom(ontology, factory.getOWLDataPropertyAssertionAxiom(
                            hasSeverityProp, contrInd, contr.severity));

                    // annotation
                    Set<OWLAnnotationAssertionAxiom> condAnnots =
                            ontology.getAnnotationAssertionAxioms(subCl.getIRI());
                    OWLLiteral condName = null;
                    for (OWLAnnotationAssertionAxiom ann : condAnnots) {
                        condName = ann.getValue().asLiteral().get();
                        break;
                    }
                    if (condName != null) {
                        manager.addAxiom(ontology,
                                factory.getOWLAnnotationAssertionAxiom(
                                        labelProp, condInd.getIRI(), condName));
                    }

                    contrCondMap.put(contrInd, subCl);
                }
            }
        }

        for (OWLNamedIndividual contrInd : contrCondMap.keySet()) {
            OWLClass cl = contrCondMap.get(contrInd);
            if (cls1.contains(cl)) {
                manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(givenContrClass, contrInd));
            } else {
                manager.addAxiom(ontology, factory.getOWLClassAssertionAxiom(inferredContrClass, contrInd));
            }
        }

        Out.p((cls2.size() - cls1.size()) + " out of " + cls2.size()
                + "  conditions are inferred to have contraindications");

        // save the ontology
        if (!ontFile.exists()) {
            ontFile.createNewFile();
        }

        FileOutputStream outputStream = new FileOutputStream(ontFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(ontology, buffOutputStream);
    }





    private OWLClass findICD9Class(String code) {
        return icd9Map.get(code);
    }


    public static void main(String args[])
            throws Exception {
        DrugContraindicationMapper converter = new DrugContraindicationMapper(
                new File(args[0]), new File(args[1]), new File(args[2]));
        converter.addICD9Ontology(new File(args[3]));
        converter.computeClassHierarchy();
        converter.createMekonOntology(new File(args[4]));
    }




}
