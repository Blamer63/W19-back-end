package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Entity
@Table(name = "languages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Language {

    @Id
    @Column(length = 10)
    private String code; // e.g., "en", "es"

    @Column(nullable = false)
    private String name; // e.g., "English", "Spanish"

    @Column(name = "native_name")
    private String nativeName; // e.g., "English", "Español"

    @Column(name = "flag_emoji")
    private String flagEmoji;
}
