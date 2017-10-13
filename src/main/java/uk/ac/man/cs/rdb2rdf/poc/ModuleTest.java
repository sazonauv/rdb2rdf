package uk.ac.man.cs.rdb2rdf.poc;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Created by slava on 13/10/17.
 */
public class ModuleTest {


    public static void main(String[] args) throws OWLOntologyCreationException {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File(args[0]));
        SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(manager,
                ontology, ModuleType.BOT);
        Set<OWLEntity> sig = new HashSet<>();
        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLClass A = factory.getOWLClass(IRI.create("http://www.semanticweb.org/slava/ontologies/2017/9/untitled-ontology-236#A"));
        OWLClass B = factory.getOWLClass(IRI.create("http://www.semanticweb.org/slava/ontologies/2017/9/untitled-ontology-236#B"));
        sig.add(A);
        sig.add(B);
        Set<OWLAxiom> modAxioms = extractor.extractAsOntology(sig, IRI.create(UUID.randomUUID().toString())).getAxioms();
        System.out.println("module size = " + modAxioms.size());
    }

}
