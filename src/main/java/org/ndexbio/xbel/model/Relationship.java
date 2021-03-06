//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.7 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.12.03 at 11:33:45 AM PST 
//


package org.ndexbio.xbel.model;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for relationship.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="relationship">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="increases"/>
 *     &lt;enumeration value="decreases"/>
 *     &lt;enumeration value="directlyIncreases"/>
 *     &lt;enumeration value="directlyDecreases"/>
 *     &lt;enumeration value="causesNoChange"/>
 *     &lt;enumeration value="positiveCorrelation"/>
 *     &lt;enumeration value="negativeCorrelation"/>
 *     &lt;enumeration value="translatedTo"/>
 *     &lt;enumeration value="transcribedTo"/>
 *     &lt;enumeration value="isA"/>
 *     &lt;enumeration value="subProcessOf"/>
 *     &lt;enumeration value="rateLimitingStepOf"/>
 *     &lt;enumeration value="biomarkerFor"/>
 *     &lt;enumeration value="prognosticBiomarkerFor"/>
 *     &lt;enumeration value="orthologous"/>
 *     &lt;enumeration value="analogous"/>
 *     &lt;enumeration value="association"/>
 *     &lt;enumeration value="hasMembers"/>
 *     &lt;enumeration value="hasComponents"/>
 *     &lt;enumeration value="hasMember"/>
 *     &lt;enumeration value="hasComponent"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "relationship")
@XmlEnum
public enum Relationship {

    @XmlEnumValue("increases")
    INCREASES("increases"),
    @XmlEnumValue("decreases")
    DECREASES("decreases"),
    @XmlEnumValue("directlyIncreases")
    DIRECTLY_INCREASES("directlyIncreases"),
    @XmlEnumValue("directlyDecreases")
    DIRECTLY_DECREASES("directlyDecreases"),
    @XmlEnumValue("causesNoChange")
    CAUSES_NO_CHANGE("causesNoChange"),
    @XmlEnumValue("positiveCorrelation")
    POSITIVE_CORRELATION("positiveCorrelation"),
    @XmlEnumValue("negativeCorrelation")
    NEGATIVE_CORRELATION("negativeCorrelation"),
    @XmlEnumValue("translatedTo")
    TRANSLATED_TO("translatedTo"),
    @XmlEnumValue("transcribedTo")
    TRANSCRIBED_TO("transcribedTo"),
    @XmlEnumValue("isA")
    IS_A("isA"),
    @XmlEnumValue("subProcessOf")
    SUB_PROCESS_OF("subProcessOf"),
    @XmlEnumValue("rateLimitingStepOf")
    RATE_LIMITING_STEP_OF("rateLimitingStepOf"),
    @XmlEnumValue("biomarkerFor")
    BIOMARKER_FOR("biomarkerFor"),
    @XmlEnumValue("prognosticBiomarkerFor")
    PROGNOSTIC_BIOMARKER_FOR("prognosticBiomarkerFor"),
    @XmlEnumValue("orthologous")
    ORTHOLOGOUS("orthologous"),
    @XmlEnumValue("analogous")
    ANALOGOUS("analogous"),
    @XmlEnumValue("association")
    ASSOCIATION("association"),
    @XmlEnumValue("hasMembers")
    HAS_MEMBERS("hasMembers"),
    @XmlEnumValue("hasComponents")
    HAS_COMPONENTS("hasComponents"),
    @XmlEnumValue("hasMember")
    HAS_MEMBER("hasMember"),
    @XmlEnumValue("hasComponent")
    HAS_COMPONENT("hasComponent");
    private final String value;

    Relationship(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static Relationship fromValue(String v) {
        for (Relationship c: Relationship.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
