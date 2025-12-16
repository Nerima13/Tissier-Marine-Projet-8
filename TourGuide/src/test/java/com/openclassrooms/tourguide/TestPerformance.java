package com.openclassrooms.tourguide;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.service.RewardsService;
import com.openclassrooms.tourguide.service.TourGuideService;
import com.openclassrooms.tourguide.user.User;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.VisitedLocation;
import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rewardCentral.RewardCentral;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestPerformance {

    private final Logger logger = LoggerFactory.getLogger(TestPerformance.class);

    private final GpsUtil gpsUtil = new GpsUtil();
    private final RewardsService rewardsService = new RewardsService(gpsUtil, new RewardCentral());

    /*
     * Performance targets:
     *
     * highVolumeTrackLocation: 100,000 users within 15 minutes
     * highVolumeGetRewards:   100,000 users within 20 minutes
     */

    @Test
    @Disabled
    public void highVolumeTrackLocation() {
        // GIVEN
        InternalTestHelper.setInternalUserNumber(100000);
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        List<User> users = tourGuideService.getAllUsers();

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // WHEN - locations are tracked in parallel inside the service
        tourGuideService.trackUserLocations(users);

        stopWatch.stop();
        long timeInSeconds = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());

        logger.info("highVolumeTrackLocation: {} seconds", timeInSeconds);

        // THEN
        assertTrue(TimeUnit.MINUTES.toSeconds(15) >= timeInSeconds,
                "Tracking user locations for 100,000 users took too long: " + timeInSeconds + " seconds");

        tourGuideService.tracker.stopTracking();
    }

    @Test
    @Disabled
    public void highVolumeGetRewards() {
        // GIVEN
        InternalTestHelper.setInternalUserNumber(100000);
        TourGuideService tourGuideService = new TourGuideService(gpsUtil, rewardsService);

        List<User> users = tourGuideService.getAllUsers();

        // Force each user to have at least one visited location at a known attraction, just like in the original test.
        Attraction attraction = gpsUtil.getAttractions().get(0);
        Date now = new Date();
        for (User user : users) {
            user.addToVisitedLocations(
                    new VisitedLocation(user.getUserId(), attraction, now));
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        // WHEN - rewards are calculated in parallel inside the service
        rewardsService.calculateRewardsForAllUsers(users);

        stopWatch.stop();
        long timeInSeconds = TimeUnit.MILLISECONDS.toSeconds(stopWatch.getTime());

        logger.info("highVolumeGetRewards: {} seconds", timeInSeconds);

        // THEN: every user should have at least one reward
        for (User user : users) {
            assertTrue(user.getUserRewards().size() > 0,
                    "User " + user.getUserName() + " has no rewards");
        }

        assertTrue(TimeUnit.MINUTES.toSeconds(20) >= timeInSeconds,
                "Calculating rewards for 100,000 users took too long: " + timeInSeconds + " seconds");

        tourGuideService.tracker.stopTracking();
    }
}