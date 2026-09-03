package com.aierp;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(schema = "platform", name = "querydsl_probe")
class QuerydslProbe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String label;

    protected QuerydslProbe() {
    }

    QuerydslProbe(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }
}
