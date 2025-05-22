export default { "schemeTypes": [
    { "label": "HealthID",
      "value": "urn:eu.europa.ec.eudi:hiid:1",
      "sd": false,
      "attributes": [
        { "label": "health_insurance_id", "value": "health_insurance_id" },
        { "label": "patient_id", "value": "patient_id" },
        { "label": "tax_number", "value": "tax_number" },
        { "label": "one_time_token", "value": "one_time_token", "isSelected": true },
        { "label": "wallet_e_prescription_code", "value": "wallet_e_prescription_code" },
        { "label": "affiliation_country", "value": "affiliation_country", "isSelected": true  },
        { "label": "issue_date", "value": "issue_date", "isSelected": true  },
        { "label": "expiry_date", "value": "expiry_date", "isSelected": true  },
        { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true  },
        { "label": "document_number", "value": "document_number" },
        { "label": "administrative_number", "value": "administrative_number" },
        { "label": "issuing_country", "value": "issuing_country", "isSelected": true  },
        { "label": "issuing_jurisdiction", "value": "issuing_jurisdiction" }
      ]
    },
    { "label": "European Health Insurance Card (EHIC)",
      "value": "urn:eudi:ehic:1",
      "sd": false,
      "attributes": [
        { "label": "issuing_country", "value": "issuing_country" },
        { "label": "social_security_number", "value": "social_security_number" },
        { "label": "issuing_authority", "value": "issuing_authority" },
        { "label": "document_number", "value": "document_number" },
        { "label": "issuance_date", "value": "issuance_date" },
        { "label": "expiry_date", "value": "expiry_date" },
      ]
    },
    { "label": "mDL",
      "value": "org.iso.18013.5.1.mDL",
      "sd": true,
      "attributes": [
        { "label": "family_name", "value": "family_name", "isSelected": true },
        { "label": "given_name", "value": "given_name", "isSelected": true },
        { "label": "birth_date", "value": "birth_date", "isSelected": true },
        { "label": "issue_date", "value": "issue_date", "isSelected": true },
        { "label": "expiry_date", "value": "expiry_date", "isSelected": true },
        { "label": "issuing_country", "value": "issuing_country", "isSelected": true },
        { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true },
        { "label": "document_number", "value": "document_number", "isSelected": true },
        { "label": "portrait", "value": "portrait", "isSelected": true },
        { "label": "driving_privileges", "value": "driving_privileges", "isSelected": true },
        { "label": "un_distinguishing_sign", "value": "un_distinguishing_sign", "isSelected": true },
        { "label": "administrative_number", "value": "administrative_number" },
        { "label": "sex", "value": "sex" },
        { "label": "height", "value": "height" },
        { "label": "weight", "value": "weight" },
        { "label": "eye_colour", "value": "eye_colour" },
        { "label": "hair_colour", "value": "hair_colour" },
        { "label": "birth_place", "value": "birth_place" },
        { "label": "resident_address", "value": "resident_address" },
        { "label": "portrait_capture_date", "value": "portrait_capture_date" },
        { "label": "age_in_years", "value": "age_in_years" },
        { "label": "age_birth_year", "value": "age_birth_year" },
        { "label": "age_over_12", "value": "age_over_12" },
        { "label": "age_over_13", "value": "age_over_13" },
        { "label": "age_over_14", "value": "age_over_14" },
        { "label": "age_over_16", "value": "age_over_16" },
        { "label": "age_over_18", "value": "age_over_18" },
        { "label": "age_over_21", "value": "age_over_21" },
        { "label": "age_over_25", "value": "age_over_25" },
        { "label": "age_over_60", "value": "age_over_60" },
        { "label": "age_over_62", "value": "age_over_62" },
        { "label": "age_over_65", "value": "age_over_65" },
        { "label": "age_over_68", "value": "age_over_68" },
        { "label": "issuing_jurisdiction", "value": "issuing_jurisdiction" },
        { "label": "nationality", "value": "nationality" },
        { "label": "resident_city", "value": "resident_city" },
        { "label": "resident_state", "value": "resident_state" },
        { "label": "resident_postal_code", "value": "resident_postal_code" },
        { "label": "resident_country", "value": "resident_country" },
        { "label": "biometric_template_face", "value": "biometric_template_face" },
        { "label": "biometric_template_finger", "value": "biometric_template_finger" },
        { "label": "biometric_template_signature_sign", "value": "biometric_template_signature_sign" },
        { "label": "biometric_template_iris", "value": "biometric_template_iris" },
        { "label": "family_name_national_character", "value": "family_name_national_character" },
        { "label": "given_name_national_character", "value": "given_name_national_character" },
        { "label": "signature_usual_mark", "value": "signature_usual_mark" }
      ]
    },
    { "label": "PID",
      "value": "urn:eu.europa.ec.eudi:pid:1",
      "sd": true,
      "attributes": [
        { "label": "family_name", "value": "family_name", "isSelected": true },
        { "label": "given_name", "value": "given_name", "isSelected": true },
        { "label": "birth_date", "value": "birth_date", "isSelected": true },
        { "label": "portrait", "value": "portrait" },
        { "label": "portrait_capture_date", "value": "portrait_capture_date" },
        { "label": "age_over_12", "value": "age_over_12" },
        { "label": "age_over_13", "value": "age_over_13" },
        { "label": "age_over_14", "value": "age_over_14" },
        { "label": "age_over_16", "value": "age_over_16" },
        { "label": "age_over_18", "value": "age_over_18"},
        { "label": "age_over_21", "value": "age_over_21" },
        { "label": "age_over_25", "value": "age_over_25" },
        { "label": "age_over_60", "value": "age_over_60" },
        { "label": "age_over_62", "value": "age_over_62" },
        { "label": "age_over_65", "value": "age_over_65" },
        { "label": "age_over_68", "value": "age_over_68" },
        { "label": "age_in_years", "value": "age_in_years" },
        { "label": "age_birth_year", "value": "age_birth_year" },
        { "label": "family_name_birth", "value": "family_name_birth" },
        { "label": "given_name_birth", "value": "given_name_birth" },
        { "label": "birth_place", "value": "birth_place" },
        { "label": "resident_address", "value": "resident_address" },
        { "label": "resident_country", "value": "resident_country" },
        { "label": "resident_state", "value": "resident_state" },
        { "label": "resident_city", "value": "resident_city" },
        { "label": "resident_postal_code", "value": "resident_postal_code" },
        { "label": "resident_street", "value": "resident_street" },
        { "label": "resident_house_number", "value": "resident_house_number" },
        { "label": "sex", "value": "sex" },
        { "label": "nationality", "value": "nationality", "isSelected": true },
        { "label": "issuance_date", "value": "issuance_date" },
        { "label": "expiry_date", "value": "expiry_date", "isSelected": true },
        { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true },
        { "label": "document_number", "value": "document_number" },
        { "label": "issuing_country", "value": "issuing_country", "isSelected": true },
        { "label": "issuing_jurisdiction", "value": "issuing_jurisdiction" },
        { "label": "personal_administrative_number", "value": "personal_administrative_number" },
        { "label": "email_address", "value": "email_address" },
        { "label": "mobile_phone_number", "value": "mobile_phone_number" },
        { "label": "trust_anchor", "value": "trust_anchor" },
        { "label": "location_status", "value": "location_status" }
      ]
    },
    { "label": "PID (SD-JWT, ARF 1.8.0)",
      "value": "urn:eudi:pid:1",
      "sd": true,
      "attributes": [
        { "label": "family_name", "value": "family_name", "isSelected": true },
        { "label": "given_name", "value": "given_name", "isSelected": true },
        { "label": "birthdate", "value": "birthdate", "isSelected": true },
        { "label": "picture", "value": "picture" },
        { "label": "age_equal_or_over.12", "value": "age_equal_or_over.12" },
        { "label": "age_equal_or_over.13", "value": "age_equal_or_over.13" },
        { "label": "age_equal_or_over.14", "value": "age_equal_or_over.14" },
        { "label": "age_equal_or_over.16", "value": "age_equal_or_over.16" },
        { "label": "age_equal_or_over.18", "value": "age_equal_or_over.18" },
        { "label": "age_equal_or_over.21", "value": "age_equal_or_over.21" },
        { "label": "age_equal_or_over.25", "value": "age_equal_or_over.25" },
        { "label": "age_equal_or_over.60", "value": "age_equal_or_over.60" },
        { "label": "age_equal_or_over.62", "value": "age_equal_or_over.62" },
        { "label": "age_equal_or_over.65", "value": "age_equal_or_over.65" },
        { "label": "age_equal_or_over.68", "value": "age_equal_or_over.68" },
        { "label": "age_in_years", "value": "age_in_years" },
        { "label": "age_birth_year", "value": "age_birth_year" },
        { "label": "birth_family_name", "value": "birth_family_name" },
        { "label": "birth_given_name", "value": "birth_given_name" },
        { "label": "place_of_birth.country", "value": "place_of_birth.country" },
        { "label": "place_of_birth.region", "value": "place_of_birth.region" },
        { "label": "place_of_birth.locality", "value": "place_of_birth.locality", "isSelected": true },
        { "label": "address.formatted", "value": "address.formatted" },
        { "label": "address.country", "value": "address.country" },
        { "label": "address.region", "value": "address.region" },
        { "label": "address.locality", "value": "address.locality" },
        { "label": "address.postal_code", "value": "address.postal_code" },
        { "label": "address.street", "value": "address.street" },
        { "label": "address.house_number", "value": "address.house_number" },
        { "label": "sex", "value": "sex" },
        { "label": "nationalities", "value": "nationalities", "isSelected": true },
        { "label": "date_of_issuance", "value": "date_of_issuance" },
        { "label": "date_of_expiry", "value": "date_of_expiry", "isSelected": true },
        { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true },
        { "label": "document_number", "value": "document_number" },
        { "label": "issuing_country", "value": "issuing_country", "isSelected": true },
        { "label": "issuing_jurisdiction", "value": "issuing_jurisdiction" },
        { "label": "personal_administrative_number", "value": "personal_administrative_number" },
        { "label": "email", "value": "email" },
        { "label": "phone_number", "value": "phone_number" },
        { "label": "trust_anchor", "value": "trust_anchor" }
      ]
    },
    { "label": "Power of Representation (PoR)",
      "value": "urn:eu.europa.ec.eudi:por:1",
      "sd": false,
      "attributes": [
        { "label": "legal_person_identifier", "value": "legal_person_identifier" },
        { "label": "legal_name", "value": "legal_name" },
        { "label": "full_powers", "value": "full_powers" },
        { "label": "eService", "value": "eService" },
        { "label": "effective_from_date", "value": "effective_from_date" },
        { "label": "effective_until_date", "value": "effective_until_date" },
        { "label": "issuance_date", "value": "issuance_date" },
        { "label": "expiry_date", "value": "expiry_date" },
        { "label": "issuing_authority", "value": "issuing_authority" },
        { "label": "issuing_country", "value": "issuing_country" },
        { "label": "issuing_jurisdiction", "value": "issuing_jurisdiction" },
        { "label": "document_number", "value": "document_number" },
        { "label": "administrative_number", "value": "administrative_number" }
      ]
    },
    { "label": "Certificate of Residence (CoR)",
      "value": "eu.europa.ec.eudi.cor.1",
      "sd": true,
      "attributes": [
        { "label": "family_name", "value": "family_name", "isSelected": true },
        { "label": "given_name", "value": "given_name", "isSelected": true },
        { "label": "birth_date", "value": "birth_date", "isSelected": true },
        { "label": "nationality", "value": "nationality" },
        { "label": "birth_place", "value": "birth_place" },
        { "label": "arrival_date", "value": "arrival_date" },
        { "label": "residence_address", "value": "residence_address", "isSelected": true },
        { "label": "residence_address.po_box", "value": "residence_address.po_box" },
        { "label": "residence_address.thoroughfare", "value": "residence_address.thoroughfare" },
        { "label": "residence_address.locator_designator", "value": "residence_address.locator_designator" },
        { "label": "residence_address.locator_name", "value": "residence_address.locator_name" },
        { "label": "residence_address.post_code", "value": "residence_address.post_code" },
        { "label": "residence_address.post_name", "value": "residence_address.post_name" },
        { "label": "residence_address.admin_unit_L1", "value": "residence_address.admin_unit_L1" },
        { "label": "residence_address.admin_unit_L2", "value": "residence_address.admin_unit_L2" },
        { "label": "residence_address.full_address", "value": "residence_address.full_address" },
        { "label": "gender", "value": "gender" },
        { "label": "issuance_date", "value": "issuance_date", "isSelected": true },
        { "label": "expiry_date", "value": "expiry_date", "isSelected": true },
        { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true },
        { "label": "issuing_country", "value": "issuing_country", "isSelected": true },
        { "label": "issuing_jurisdiction", "value": "issuing_jurisdiction" },
        { "label": "document_number", "value": "document_number" },
        { "label": "administrative_number", "value": "administrative_number" }
      ]
    },
    { "label": "Company Registration (CRA)",
      "value": "urn:eu.europa.ec.eudi:cr:1",
      "sd": true,
      "attributes": [
        { "label": "company_name", "value": "company_name", "isSelected": true },
        { "label": "company_type", "value": "company_type", "isSelected": true },
        { "label": "company_status", "value": "company_status", "isSelected": true },
        { "label": "company_activity", "value": "company_activity", "isSelected": true },
        { "label": "registration_date", "value": "registration_date", "isSelected": true },
        { "label": "company_end_date", "value": "company_end_date" },
        { "label": "company_EUID", "value": "company_EUID", "isSelected": true },
        { "label": "vat_number", "value": "vat_number" },
        { "label": "company_contact_data", "value": "company_contact_data" },
        { "label": "registered_address", "value": "registered_address", "isSelected": true },
        { "label": "postal_address", "value": "postal_address" },
        { "label": "branch", "value": "branch" }
      ]
    },
    { "label": "Tax Identified (TAX ID)",
      "value": "urn:eu.europa.ec.eudi:tax:1",
      "sd": false,
      "attributes": [
        { "label": "tax_number", "value": "tax_number" },
        { "label": "affiliation_country", "value": "affiliation_country" },
        { "label": "registered_family_name", "value": "registered_family_name" },
        { "label": "registered_given_name", "value": "registered_given_name" },
        { "label": "resident_address", "value": "resident_address" },
        { "label": "birth_date", "value": "birth_date" },
        { "label": "issuance_date", "value": "issuance_date" },
        { "label": "expiry_date", "value": "expiry_date" },
        { "label": "issuing_authority", "value": "issuing_authority" },
        { "label": "issuing_country", "value": "issuing_country" },
        { "label": "administrative_number", "value": "administrative_number" },
        { "label": "church_tax_ID", "value": "church_tax_ID" },
        { "label": "iban", "value": "iban" },
        { "label": "pid_id", "value": "pid_id" },
        { "label": "document_number", "value": "document_number" },
        { "label": "issuing_jurisdiction", "value": "issuing_jurisdiction" },
      ]
    }
  ],
  "presentationMechanisms": [
    { "label": "Presentation Exchange", "value": "presentation_definition" },
    { "label": "DCQL", "value": "dcql_query" }
  ],
  "representation": [
    { "label": "SD-JWT", "value": "SD_JWT" },
    { "label": "ISO mDoc", "value": "ISO_MDOC" }
  ],
  "profiles": [
    { "label": "Custom",
      "simple": false,
      "credentials": [
        {
          "schemeType": null,
          "representation": null,
          "sd": true,
          "attributes": []
        }
      ]
    },
    { "label": "HealthID",
      "simple": false,
      "credentials": [
        {
          "schemeType": "urn:eu.europa.ec.eudi:hiid:1",
          "representation": "SD_JWT",
          "sd": false,
          "attributes": [
            { "label": "one_time_token", "value": "one_time_token", "isSelected": true  },
            { "label": "affiliation_country", "value": "affiliation_country", "isSelected": true  },
            { "label": "issue_date", "value": "issue_date", "isSelected": true  },
            { "label": "expiry_date", "value": "expiry_date", "isSelected": true  },
            { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true  },
            { "label": "issuing_country", "value": "issuing_country", "isSelected": true  },
          ]
        }
      ]
    },
    { "label": "European Health Insurance Card (EHIC)",
      "simple": false,
      "credentials": [
        {
          "schemeType": "urn:eudi:ehic:1",
          "representation": "SD_JWT",
          "sd": false,
          "attributes": [
            { "label": "issuing_country", "value": "issuing_country" },
            { "label": "social_security_number", "value": "social_security_number" },
            { "label": "issuing_authority", "value": "issuing_authority" },
            { "label": "document_number", "value": "document_number" },
            { "label": "issuance_date", "value": "issuance_date" },
            { "label": "expiry_date", "value": "expiry_date" },
          ]
        }
      ]
    },
    { "label": "mDL Mandatory",
      "simple": false,
      "credentials": [
        {
          "schemeType": "org.iso.18013.5.1.mDL",
          "representation": "ISO_MDOC",
          "sd": true,
          "attributes": [
            { "label": "family_name", "value": "family_name", "isSelected": true },
            { "label": "given_name", "value": "given_name", "isSelected": true },
            { "label": "birth_date", "value": "birth_date", "isSelected": true },
            { "label": "issue_date", "value": "issue_date", "isSelected": true },
            { "label": "expiry_date", "value": "expiry_date", "isSelected": true },
            { "label": "issuing_country", "value": "issuing_country", "isSelected": true },
            { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true },
            { "label": "document_number", "value": "document_number", "isSelected": true },
            { "label": "portrait", "value": "portrait", "isSelected": true },
            { "label": "driving_privileges", "value": "driving_privileges", "isSelected": true },
            { "label": "un_distinguishing_sign", "value": "un_distinguishing_sign", "isSelected": true },
          ]
        }
      ]
    },
    { "label": "PID Mandatory",
      "simple": false,
      "credentials": [
        {
          "schemeType": "urn:eu.europa.ec.eudi:pid:1",
          "representation": "SD_JWT",
          "sd": true,
          "attributes": [
            { "label": "family_name", "value": "family_name", "isSelected": true },
            { "label": "given_name", "value": "given_name", "isSelected": true },
            { "label": "birth_date", "value": "birth_date", "isSelected": true },
            { "label": "nationality", "value": "nationality", "isSelected": true },
            { "label": "expiry_date", "value": "expiry_date", "isSelected": true },
            { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true },
            { "label": "issuing_country", "value": "issuing_country", "isSelected": true },
          ]
        }
      ]
    },
    { "label": "PoR Mandatory",
      "simple": false,
      "credentials": [
        {
          "schemeType": "urn:eu.europa.ec.eudi:por:1",
          "representation": "SD_JWT",
          "sd": false,
          "attributes": [
            { "label": "legal_person_identifier", "value": "legal_person_identifier" },
            { "label": "legal_name", "value": "legal_name" },
            { "label": "full_powers", "value": "full_powers" },
            { "label": "effective_from_date", "value": "effective_from_date" },
            { "label": "issuance_date", "value": "issuance_date" },
            { "label": "expiry_date", "value": "expiry_date" },
            { "label": "issuing_authority", "value": "issuing_authority" },
            { "label": "issuing_country", "value": "issuing_country" },
          ]
        }
      ]
    },
    { "label": "CoR Mandatory",
      "simple": false,
      "credentials": [
        {
          "schemeType": "eu.europa.ec.eudi.cor.1",
          "representation": "SD_JWT",
          "sd": true,
          "attributes": [
            { "label": "family_name", "value": "family_name", "isSelected": true },
            { "label": "given_name", "value": "given_name", "isSelected": true },
            { "label": "birth_date", "value": "birth_date", "isSelected": true },
            { "label": "residence_address", "value": "residence_address", "isSelected": true },
            { "label": "issuance_date", "value": "issuance_date", "isSelected": true },
            { "label": "expiry_date", "value": "expiry_date", "isSelected": true },
            { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true },
            { "label": "issuing_country", "value": "issuing_country", "isSelected": true },
          ]
        }
      ]
    },
    { "label": "CRA Mandatory",
      "simple": false,
      "credentials": [
        {
          "schemeType": "urn:eu.europa.ec.eudi:cr:1",
          "representation": "SD_JWT",
          "sd": true,
          "attributes": [
            { "label": "company_name", "value": "company_name", "isSelected": true },
            { "label": "company_type", "value": "company_type", "isSelected": true },
            { "label": "company_status", "value": "company_status", "isSelected": true },
            { "label": "company_activity", "value": "company_activity", "isSelected": true },
            { "label": "registration_date", "value": "registration_date", "isSelected": true },
            { "label": "company_EUID", "value": "company_EUID", "isSelected": true },
            { "label": "registered_address", "value": "registered_address", "isSelected": true },
          ]
        }
      ]
    },
    { "label": "Combined PID+PoR",
      "simple": false,
      "credentials": [
        {
          "schemeType": "urn:eu.europa.ec.eudi:pid:1",
          "representation": "SD_JWT",
          "sd": true,
          "attributes": [
            { "label": "family_name", "value": "family_name", "isSelected": true },
            { "label": "given_name", "value": "given_name", "isSelected": true },
            { "label": "birth_date", "value": "birth_date", "isSelected": true },
            { "label": "nationality", "value": "nationality", "isSelected": true },
            { "label": "expiry_date", "value": "expiry_date", "isSelected": true },
            { "label": "issuing_authority", "value": "issuing_authority", "isSelected": true },
            { "label": "issuing_country", "value": "issuing_country", "isSelected": true }
          ]
        },
        {
          "schemeType": "urn:eu.europa.ec.eudi:por:1",
          "representation": "SD_JWT",
          "sd": false,
          "attributes": [
            { "label": "legal_person_identifier", "value": "legal_person_identifier" },
            { "label": "legal_name", "value": "legal_name" },
            { "label": "full_powers", "value": "full_powers" },
            { "label": "effective_from_date", "value": "effective_from_date" },
            { "label": "issuance_date", "value": "issuance_date" },
            { "label": "expiry_date", "value": "expiry_date" },
            { "label": "issuing_authority", "value": "issuing_authority" },
            { "label": "issuing_country", "value": "issuing_country" }
          ]
        }
      ]
    }
  ]
}
