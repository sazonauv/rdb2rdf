package uk.ac.man.cs.rdb2rdf.poc;

import org.semanticweb.owlapi.model.OWLAnnotation;
import org.semanticweb.owlapi.model.OWLAxiom;

import java.util.Comparator;
import java.util.Set;

import static uk.ac.man.cs.rdb2rdf.poc.CSV2OWLConverter.DAY;
import static uk.ac.man.cs.rdb2rdf.poc.CSV2OWLConverter.MONTH;
import static uk.ac.man.cs.rdb2rdf.poc.CSV2OWLConverter.YEAR;

/**
 * Created by slava on 06/10/17.
 */
public class EventDateComparator implements Comparator<OWLAxiom> {

    private boolean isAscendingOrder;

    public EventDateComparator(boolean isAscendingOrder) {
        this.isAscendingOrder = isAscendingOrder;
    }

    private int isGreater(OWLAxiom axiom1, OWLAxiom axiom2) {
        final Set<OWLAnnotation> anns1 = axiom1.getAnnotations();
        final Set<OWLAnnotation> anns2 = axiom2.getAnnotations();
        int year1 = -1;
        int year2 = -1;
        int month1 = -1;
        int month2 = -1;
        int day1 = -1;
        int day2 = -1;
        for (OWLAnnotation ann1 : anns1) {
            for (OWLAnnotation ann2 : anns2) {
                if (ann1.getProperty().equals(ann2.getProperty())) {
                    if (ann1.getProperty().getIRI().getShortForm().equals(YEAR)) {
                        year1 = ann1.getValue().asLiteral().get().parseInteger();
                        year2 = ann2.getValue().asLiteral().get().parseInteger();
                    }
                    if (ann1.getProperty().getIRI().getShortForm().equals(MONTH)) {
                        month1 = ann1.getValue().asLiteral().get().parseInteger();
                        month2 = ann2.getValue().asLiteral().get().parseInteger();
                    }
                    if (ann1.getProperty().getIRI().getShortForm().equals(DAY)) {
                        day1 = ann1.getValue().asLiteral().get().parseInteger();
                        day2 = ann2.getValue().asLiteral().get().parseInteger();
                    }
                }
            }
        }
        if (year1 > year2) {
            return 1;
        } else if (year1 < year2) {
            return -1;
        } else {
            if (month1 > month2) {
                return 1;
            } else if (month1 < month2) {
                return -1;
            } else {
                if (day1 > day2) {
                    return 1;
                } else if (day1 < day2) {
                    return -1;
                } else {
                    return 0;
                }
            }
        }
    }

    @Override
    public int compare(OWLAxiom axiom1, OWLAxiom axiom2) {
        if (isAscendingOrder) {
            return isGreater(axiom1, axiom2);
        } else {
            return - isGreater(axiom1, axiom2);
        }
    }

}
