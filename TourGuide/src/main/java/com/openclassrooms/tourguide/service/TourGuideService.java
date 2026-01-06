package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.helper.InternalTestHelper;
import com.openclassrooms.tourguide.tracker.Tracker;
import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;

import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tripPricer.Provider;
import tripPricer.TripPricer;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class TourGuideService {

    private final Logger logger = LoggerFactory.getLogger(TourGuideService.class);

    private final GpsUtil gpsUtil;
    private final RewardsService rewardsService;
    private final TripPricer tripPricer = new TripPricer();
    public final Tracker tracker;
    boolean testMode = true;

    // Thread pool used to process multiple users in parallel
    private final ExecutorService locationExecutor =
            Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    public TourGuideService(GpsUtil gpsUtil, RewardsService rewardsService) {
        this.gpsUtil = gpsUtil;
        this.rewardsService = rewardsService;

        Locale.setDefault(Locale.US);

        if (testMode) {
            logger.info("TestMode enabled");
            logger.debug("Initializing users");
            initializeInternalUsers();
            logger.debug("Finished initializing users");
        }
        tracker = new Tracker(this);
        addShutDownHook();
    }

    public List<UserReward> getUserRewards(User user) {
        return user.getUserRewards();
    }

    public VisitedLocation getUserLocation(User user) {
        VisitedLocation visitedLocation = (user.getVisitedLocations().size() > 0)
                ? user.getLastVisitedLocation()
                : trackUserLocation(user);
        return visitedLocation;
    }

    public User getUser(String userName) {
        return internalUserMap.get(userName);
    }

    /**
     * Returns a copy to avoid concurrency issues (Tracker thread vs test thread).
     */
    public List<User> getAllUsers() {
        return new ArrayList<>(internalUserMap.values());
    }

    /**
     * Thread-safe "add if absent" (avoids race condition).
     */
    public void addUser(User user) {
        internalUserMap.putIfAbsent(user.getUserName(), user);
    }

    public List<Provider> getTripDeals(User user) {
        int cumulatativeRewardPoints = user.getUserRewards().stream()
                .mapToInt(UserReward::getRewardPoints)
                .sum();

        List<Provider> providers = tripPricer.getPrice(
                tripPricerApiKey,
                user.getUserId(),
                user.getUserPreferences().getNumberOfAdults(),
                user.getUserPreferences().getNumberOfChildren(),
                user.getUserPreferences().getTripDuration(),
                cumulatativeRewardPoints);
        user.setTripDeals(providers);
        return providers;
    }

    public VisitedLocation trackUserLocation(User user) {
        VisitedLocation visitedLocation = gpsUtil.getUserLocation(user.getUserId());
        user.addToVisitedLocations(visitedLocation);
        rewardsService.calculateRewards(user);
        return visitedLocation;
    }

    /**
     * Track the location of all given users in parallel.
     * Used for performance scenarios (100k users).
     */
    public void trackUserLocations(List<User> users) {
        List<CompletableFuture<Void>> futures = users.stream()
                .map(user -> CompletableFuture.runAsync(() -> trackUserLocation(user), locationExecutor))
                .collect(Collectors.toList());

        // Wait for all tasks to complete
        futures.forEach(CompletableFuture::join);
    }

    /**
     * Returns the 5 closest attractions from the given visited location,
     * regardless of the distance.
     */
    public List<Attraction> getNearByAttractions(VisitedLocation visitedLocation) {
        List<Attraction> attractions = gpsUtil.getAttractions();

        return attractions.stream()
                .sorted(Comparator.comparingDouble(a -> rewardsService.getDistance(a, visitedLocation.location)))
                .limit(5)
                .collect(Collectors.toList());
    }

    /**
     * Properly stop background threads when the app shuts down
     * (prevents Maven/CI from hanging).
     */
    private void addShutDownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // 1) Stop Tracker thread
            tracker.stopTracking();

            // 2) Stop executor threads cleanly
            locationExecutor.shutdown();
            try {
                if (!locationExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    locationExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                locationExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }));
    }

    /**********************************************************************************
     *
     * Methods Below: For Internal Testing
     *
     **********************************************************************************/
    private static final String tripPricerApiKey = "test-server-api-key";

    // Internal users are stored in memory (must be thread-safe)
    private final Map<String, User> internalUserMap = new ConcurrentHashMap<>();

    private void initializeInternalUsers() {
        IntStream.range(0, InternalTestHelper.getInternalUserNumber()).forEach(i -> {
            String userName = "internalUser" + i;
            String phone = "000";
            String email = userName + "@tourGuide.com";
            User user = new User(UUID.randomUUID(), userName, phone, email);
            generateUserLocationHistory(user);

            internalUserMap.put(userName, user);
        });
        logger.debug("Created " + InternalTestHelper.getInternalUserNumber() + " internal test users.");
    }

    private void generateUserLocationHistory(User user) {
        IntStream.range(0, 3).forEach(i -> {
            user.addToVisitedLocations(new VisitedLocation(
                    user.getUserId(),
                    new Location(generateRandomLatitude(), generateRandomLongitude()),
                    getRandomTime()));
        });
    }

    private double generateRandomLongitude() {
        double leftLimit = -180;
        double rightLimit = 180;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private double generateRandomLatitude() {
        double leftLimit = -85.05112878;
        double rightLimit = 85.05112878;
        return leftLimit + new Random().nextDouble() * (rightLimit - leftLimit);
    }

    private Date getRandomTime() {
        LocalDateTime localDateTime = LocalDateTime.now().minusDays(new Random().nextInt(30));
        return Date.from(localDateTime.toInstant(ZoneOffset.UTC));
    }
}