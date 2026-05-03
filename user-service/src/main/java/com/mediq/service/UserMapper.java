package com.mediq.service;

import com.mediq.dto.*;
import com.mediq.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserEntity toEntity(RegisterPatientRequest request, UserType type) {
        UserEntity user = new UserEntity();
        user.setUserType(type);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setDateOfBirth(request.dateOfBirth());

        List<UserContactEntity> contacts = request.contacts().stream()
            .map(c -> toContactEntity(c, user))
            .toList();
        user.getContacts().addAll(contacts);

        if (request.addresses() != null) {
            List<UserAddressEntity> addresses = request.addresses().stream()
                .map(a -> toAddressEntity(a, user))
                .toList();
            user.getAddresses().addAll(addresses);
        }

        return user;
    }

    public UserEntity toEntity(RegisterDoctorRequest request, UserType type) {
        UserEntity user = new UserEntity();
        user.setUserType(type);
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setDateOfBirth(request.dateOfBirth());

        List<UserContactEntity> contacts = request.contacts().stream()
            .map(c -> toContactEntity(c, user))
            .toList();
        user.getContacts().addAll(contacts);

        if (request.addresses() != null) {
            List<UserAddressEntity> addresses = request.addresses().stream()
                .map(a -> toAddressEntity(a, user))
                .toList();
            user.getAddresses().addAll(addresses);
        }

        return user;
    }

    public UserResponse toResponse(UserEntity user) {
        DoctorProfileResponse doctorProfile = null;
        if (user.getDoctorProfile() != null) {
            DoctorProfileEntity dp = user.getDoctorProfile();
            doctorProfile = new DoctorProfileResponse(
                dp.getId(), dp.getLicenseNumber(),
                dp.getLicenseExpiry(), dp.getYearsOfExperience(),
                dp.getVerificationStatus());
        }

        return new UserResponse(
            user.getId(),
            user.getKeycloakId(),
            user.getUserType(),
            user.getFirstName(),
            user.getLastName(),
            user.getDateOfBirth(),
            user.isActive(),
            user.isVerified(),
            user.getContacts().stream().map(this::toContactResponse).toList(),
            user.getAddresses().stream().map(this::toAddressResponse).toList(),
            doctorProfile,
            user.getCreatedAt()
        );
    }

    private UserContactEntity toContactEntity(ContactRequest c, UserEntity user) {
        UserContactEntity entity = new UserContactEntity();
        entity.setUser(user);
        entity.setContactType(c.contactType());
        entity.setContactValue(c.contactValue());
        entity.setPrimary(c.isPrimary());
        return entity;
    }

    private UserAddressEntity toAddressEntity(AddressRequest a, UserEntity user) {
        UserAddressEntity entity = new UserAddressEntity();
        entity.setUser(user);
        entity.setAddressType(a.addressType());
        entity.setAddressLine1(a.addressLine1());
        entity.setAddressLine2(a.addressLine2());
        entity.setCity(a.city());
        entity.setState(a.state());
        entity.setZip(a.zip());
        entity.setPrimary(a.isPrimary());
        return entity;
    }

    private ContactResponse toContactResponse(UserContactEntity c) {
        return new ContactResponse(c.getId(), c.getContactType(),
            c.getContactValue(), c.isPrimary(), c.isVerified());
    }

    private AddressResponse toAddressResponse(UserAddressEntity a) {
        return new AddressResponse(a.getId(), a.getAddressType(),
            a.getAddressLine1(), a.getAddressLine2(),
            a.getCity(), a.getState(), a.getCountry(), a.getZip(), a.isPrimary());
    }
}
