package com.openclassrooms.tourguide;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;

import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import tripPricer.Provider;

@RestController
public class TourGuideController {

    @Autowired
    TourGuideService tourGuideService;

    @Autowired
    RewardsService rewardsService;

    @RequestMapping("/")
    public String index() {
        return "Greetings from TourGuide!";
    }

    @RequestMapping("/getLocation")
    public VisitedLocation getLocation(@RequestParam String userName) {
        return tourGuideService.getUserLocation(getUser(userName));
    }

    // Implemented as requested in the TODO:
    // - No longer returns a List<Attraction>
    // - Returns the 5 closest tourist attractions to the user, no matter how far they are
    // - Response contains:
    //     * attraction name
    //     * attraction lat/long
    //     * user lat/long
    //     * distance in miles
    //     * reward points for each attraction
    @RequestMapping("/getNearbyAttractions")
    public List<NearbyAttractionResponseDTO> getNearbyAttractions(@RequestParam String userName) {
        User user = getUser(userName);
        VisitedLocation visitedLocation = tourGuideService.getUserLocation(user);

        // 5 closest attractions computed by the service
        List<Attraction> closestAttractions = tourGuideService.getNearByAttractions(visitedLocation);

        return closestAttractions.stream()
                .map(attraction -> {
                    double distance = rewardsService.getDistance(attraction, visitedLocation.location);
                    int rewardPoints = rewardsService.getRewardPoints(attraction, user);

                    return new NearbyAttractionResponseDTO(
                            attraction.attractionName,
                            attraction.latitude,
                            attraction.longitude,
                            visitedLocation.location.latitude,
                            visitedLocation.location.longitude,
                            distance,
                            rewardPoints);
                })
                .collect(Collectors.toList());
    }

    @RequestMapping("/getRewards")
    public List<UserReward> getRewards(@RequestParam String userName) {
        return tourGuideService.getUserRewards(getUser(userName));
    }

    @RequestMapping("/getTripDeals")
    public List<Provider> getTripDeals(@RequestParam String userName) {
        return tourGuideService.getTripDeals(getUser(userName));
    }

    private User getUser(String userName) {
        return tourGuideService.getUser(userName);
    }
}