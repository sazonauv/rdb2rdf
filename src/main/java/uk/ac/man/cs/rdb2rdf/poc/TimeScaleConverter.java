package uk.ac.man.cs.rdb2rdf.poc;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.parameters.Imports;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.*;

import static uk.ac.man.cs.rdb2rdf.poc.CSV2OWLConverter.*;

/**
 * Created by slava on 06/10/17.
 */
public class TimeScaleConverter {


    public static void main(String args[]) throws OWLOntologyCreationException,
            FileNotFoundException, OWLOntologyStorageException {
        convertToTimeScale(new File(args[0]), new File(args[1]));
    }

    private static void convertToTimeScale(File inputFile, File outputFile)
            throws OWLOntologyCreationException, FileNotFoundException, OWLOntologyStorageException {
        // load
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology inOnt = manager.loadOntologyFromOntologyDocument(inputFile);
        OWLDataFactory factory = manager.getOWLDataFactory();

        IRI encTopIRI = IRI.create(IRI_NAME + IRI_DELIMITER + TOP_ENCOUNTER);
        OWLClass encTopClass = factory.getOWLClass(encTopIRI);

        // create
        IRI iri = IRI.create(inOnt.getOntologyID().getOntologyIRI().get() + ENTITY_DELIMITER + "time");
        OWLOntology outOnt = manager.createOntology(iri);
        manager.addAxioms(outOnt, inOnt.getAxioms());

        // find encounters
        Set<OWLAxiom> aboxAxioms = inOnt.getABoxAxioms(Imports.INCLUDED);
        Set<OWLNamedIndividual> encInds = new HashSet<>();
        for (OWLAxiom ax : aboxAxioms) {
            if (ax.isOfType(AxiomType.CLASS_ASSERTION)) {
                OWLClassAssertionAxiom clAs = (OWLClassAssertionAxiom) ax;
                if (clAs.getClassExpression().equals(encTopClass)) {
                    encInds.add((OWLNamedIndividual) clAs.getIndividual());
                    // remove old ones
                    manager.removeAxiom(outOnt, clAs);
                }
            }
        }

        // map encounters to events
        Map<OWLNamedIndividual, List<OWLObjectPropertyAssertionAxiom>> encEventsMap = new HashMap<>();
        for (OWLAxiom ax : aboxAxioms) {
            if (ax.isOfType(AxiomType.OBJECT_PROPERTY_ASSERTION)) {
                OWLObjectPropertyAssertionAxiom opAs = (OWLObjectPropertyAssertionAxiom) ax;
                OWLNamedIndividual ind = (OWLNamedIndividual) opAs.getSubject();
                if (encInds.contains(ind)) {
                    List<OWLObjectPropertyAssertionAxiom> events = encEventsMap.get(ind);
                    if (events == null) {
                        events = new ArrayList<>();
                        encEventsMap.put(ind, events);
                    }
                    events.add(opAs);
                }
            }
        }

        // order events
        EventDateComparator comparator = new EventDateComparator(true);
        for (OWLNamedIndividual enc : encEventsMap.keySet()) {
            List<OWLObjectPropertyAssertionAxiom> events = encEventsMap.get(enc);
            events.sort(comparator);
        }

        // add timed encounters
        for (OWLNamedIndividual enc : encEventsMap.keySet()) {
            List<OWLObjectPropertyAssertionAxiom> events = encEventsMap.get(enc);
            String encStr = enc.getIRI().toString();
            int i = 0;
            int id = 1;
            while (i<events.size()-1) {
                int j = i + 1;
                while (j<events.size()-1 && comparator.compare(events.get(i), events.get(j)) == 0) {
                    j++;
                }
                for (int k=i; k<=j; k++) {
                    IRI newEncIRI = IRI.create(encStr + ENTITY_DELIMITER + id);
                    OWLNamedIndividual newEnc = factory.getOWLNamedIndividual(newEncIRI);
                    manager.addAxiom(outOnt, factory.getOWLClassAssertionAxiom(encTopClass, newEnc));
                    OWLObjectPropertyAssertionAxiom event = events.get(k);
                    OWLObjectPropertyAssertionAxiom newEvent = factory.getOWLObjectPropertyAssertionAxiom(
                            event.getProperty(), newEnc, event.getObject());
                    manager.addAxiom(outOnt, newEvent);
                }
                i = j;
                id++;
            }
            manager.removeAxioms(outOnt, new HashSet<>(events));
        }

        // save
        FileOutputStream outputStream = new FileOutputStream(outputFile);
        BufferedOutputStream buffOutputStream = new BufferedOutputStream(outputStream);
        manager.saveOntology(outOnt, buffOutputStream);
    }

}
