package com.cleanroute.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="user_preference")
public class UserPreference {
    @Id @Column(name="user_id") private UUID userId;
    @Enumerated(EnumType.STRING) @Column(name="preferred_travel_mode",nullable=false,length=16) private TravelMode preferredTravelMode=TravelMode.WALK;
    @Enumerated(EnumType.STRING) @Column(name="route_preference",nullable=false,length=16) private RoutePreference routePreference=RoutePreference.BALANCED;
    @Column(name="notifications_enabled",nullable=false) private boolean notificationsEnabled=true;
    @Column(name="pollution_sensitivity",nullable=false) private int pollutionSensitivity=3;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected UserPreference() {}
    public UserPreference(UUID userId){this.userId=userId;this.updatedAt=Instant.now();}
    public void update(TravelMode mode,RoutePreference preference,boolean notifications,int sensitivity){preferredTravelMode=mode;routePreference=preference;notificationsEnabled=notifications;pollutionSensitivity=sensitivity;updatedAt=Instant.now();}
    public UUID getUserId(){return userId;} public TravelMode getPreferredTravelMode(){return preferredTravelMode;} public RoutePreference getRoutePreference(){return routePreference;} public boolean isNotificationsEnabled(){return notificationsEnabled;} public int getPollutionSensitivity(){return pollutionSensitivity;}
}
