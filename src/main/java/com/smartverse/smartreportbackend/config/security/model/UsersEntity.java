package com.smartverse.smartreportbackend.config.security.model;


import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Entity
@Data
@Table(name = "users")
public class UsersEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String name;

    private String email;

    private String password;

    private String tenant;

    private boolean active;

    @Column(name = "user_confirm")
    private boolean userConfirm;

}
