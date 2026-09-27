package com.cleanroute.domain;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="saved_place",indexes=@Index(name="ix_saved_place_user_created",columnList="user_id, created_at"))
public class SavedPlace {
 @Id private UUID id; @Column(name="user_id",nullable=false) private UUID userId; @Column(nullable=false,length=120) private String name; @Column(nullable=false) private double latitude; @Column(nullable=false) private double longitude; @Column(length=500) private String address; @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 protected SavedPlace(){} public SavedPlace(UUID user,String name,double lat,double lon,String address){id=UUID.randomUUID();userId=user;this.name=name;latitude=lat;longitude=lon;this.address=address;createdAt=Instant.now();}
 public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getName(){return name;} public double getLatitude(){return latitude;} public double getLongitude(){return longitude;} public String getAddress(){return address;} public Instant getCreatedAt(){return createdAt;}
}
