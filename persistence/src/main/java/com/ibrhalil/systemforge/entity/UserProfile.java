package com.ibrhalil.systemforge.entity;

import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@Table(name = "t_user_profiles")
@SQLDelete(sql = "UPDATE t_user_profiles SET is_deleted = true, deleted_at = now(), version = version + 1 WHERE user_id = ? AND version = ?")
@ToString(exclude = "user")
@NoArgsConstructor
public class UserProfile extends SoftDeleteAuditEntity {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_profiles_user"))
    private User user;

    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String profilePictureUrl;

    private String address;
    private String city;
    private String country;
    private String zipCode;

    public String getFullName() {
        if (firstName == null || lastName == null) return null;
        return firstName.concat(" ").concat(lastName);
    }
}
