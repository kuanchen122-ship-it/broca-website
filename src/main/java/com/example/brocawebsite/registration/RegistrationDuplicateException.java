package com.example.brocawebsite.registration;

import java.util.List;

class RegistrationDuplicateException extends RuntimeException {

    private final List<RegistrationDuplicateCandidate> duplicates;

    RegistrationDuplicateException(String message, List<RegistrationDuplicateCandidate> duplicates) {
        super(message);
        this.duplicates = List.copyOf(duplicates);
    }

    List<RegistrationDuplicateCandidate> duplicates() {
        return duplicates;
    }
}
