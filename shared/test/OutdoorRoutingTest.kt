package test

import core.geometry.GeoPoint
import core.geometry.PolylineMath
import features.indoor.domain.NavLocationType
import features.map.presentation.MapState
import kotlin.test.*

class OutdoorRoutingTest {

    private fun stateWithRoute(): Pair<MapState, FakeIndoorRoutingRepository> {
        val repo = FakeIndoorRoutingRepository()
        val state = createTestMapState(repo)
        state.updateUserLocation(GeoPoint(50.06, 19.93))
        val store = state.getAvailableNavLocations().first { it.type == NavLocationType.STORE }
        state.requestIndoorRoute(state.userLocationNav!!, store)
        return state to repo
    }

    @Test
    fun routeFromUserLocationGoesThroughStreetsThenIndoors() {
        val (state, repo) = stateWithRoute()
        val approach = assertNotNull(state.activeApproach)
        assertEquals(1, repo.approachCalls)
        assertEquals(NavLocationType.USER_LOCATION, state.indoorRouteStartLocation?.type)
        assertEquals(approach.indoor, state.activeIndoorRoute)
        assertEquals(approach.entrance.id, state.activeIndoorRoute?.startLocation?.id)
    }

    @Test
    fun movingAlongRouteDoesNotCallBackend() {
        val (state, repo) = stateWithRoute()
        // ~285 m east along the route, 5 m off the line
        state.updateUserLocation(GeoPoint(50.06005, 19.934))
        assertEquals(1, repo.approachCalls)
        assertEquals(0, state.rerouteCount)
        val along = assertNotNull(state.outdoorProgress).alongMeters
        assertTrue(along in 270.0..300.0, "along = $along")
    }

    @Test
    fun leavingRouteTriggersOneReroute() {
        val (state, repo) = stateWithRoute()
        state.updateUserLocation(GeoPoint(50.0610, 19.934)) // ~110 m north of the line
        assertEquals(2, repo.approachCalls)
        assertEquals(1, state.rerouteCount)
        assertEquals(GeoPoint(50.0610, 19.934), state.activeApproach?.outdoor?.points?.first())
    }

    @Test
    fun simulatedWalkSwitchesToIndoorRouteAtEntranceWithoutAnotherApproachCall() {
        val (state, repo) = stateWithRoute()
        val originalApproach = assertNotNull(state.activeApproach)
        var steps = 0
        while (state.simulateStep(50.0)) steps++
        assertEquals(1, repo.approachCalls, "jitter stays within the off-route margin")
        assertTrue(steps in 12..16, "~715 m in 50 m steps, got $steps")
        assertEquals(originalApproach.outdoor.points.last(), state.userLocation)
        assertNull(state.activeApproach)
        assertNull(state.outdoorProgress)
        assertEquals(originalApproach.entrance, state.indoorRouteStartLocation)
        assertEquals(originalApproach.indoor, state.activeIndoorRoute)
    }

    @Test
    fun simulatedWrongTurnReroutesOnce() {
        val (state, repo) = stateWithRoute()
        state.simulateStep(100.0, jitterMeters = 0.0)
        state.simulateWrongTurn()
        assertEquals(2, repo.approachCalls)
        assertTrue(state.outdoorProgress!!.offRouteMeters < 1.0, "new route starts where the user is")
    }

    @Test
    fun gpsFixesDriveTheRouteAndVagueFixesDoNotReroute() {
        val repo = FakeIndoorRoutingRepository()
        val gps = FakeLocationSource()
        val state = createTestMapState(repo, gps)
        state.toggleGps()
        assertTrue(gps.running)

        gps.emit(50.06, 19.93)
        assertEquals(GeoPoint(50.06, 19.93), state.userLocation)
        assertEquals(5.0, state.gpsAccuracyMeters)

        val store = state.getAvailableNavLocations().first { it.type == NavLocationType.STORE }
        state.requestIndoorRoute(state.userLocationNav!!, store)
        assertEquals(1, repo.approachCalls)

        gps.emit(50.0610, 19.934, accuracy = 120.0) // 110 m off, but only ±120 m accurate
        assertEquals(1, repo.approachCalls)
        gps.emit(50.0610, 19.934, accuracy = 8.0)
        assertEquals(2, repo.approachCalls)

        state.toggleGps()
        assertFalse(gps.running)
        assertFalse(state.isGpsOn)
    }

    @Test
    fun gpsErrorBeforeFirstFixTurnsGpsOff() {
        val gps = FakeLocationSource()
        val state = createTestMapState(locationSource = gps)
        state.toggleGps()
        gps.fail("User denied Geolocation")
        assertFalse(state.isGpsOn)
        assertFalse(gps.running)
        assertEquals("User denied Geolocation", state.locationError)
    }

    @Test
    fun clearingLocationDropsStreetRoute() {
        val (state, _) = stateWithRoute()
        state.clearUserLocation()
        assertNull(state.activeApproach)
        assertNull(state.activeIndoorRoute)
    }

    @Test
    fun polylineSplitKeepsTotalLength() {
        val line = listOf(GeoPoint(50.0, 20.0), GeoPoint(50.0, 20.01), GeoPoint(50.01, 20.01))
        val total = PolylineMath.locate(line, line.first()).totalMeters
        val (walked, remaining) = PolylineMath.splitAt(line, 500.0)
        assertEquals(walked.last(), remaining.first())
        val walkedLength = PolylineMath.locate(walked, walked.first()).totalMeters
        val remainingLength = PolylineMath.locate(remaining, remaining.first()).totalMeters
        assertEquals(500.0, walkedLength, 2.0)
        assertEquals(total, walkedLength + remainingLength, 2.0)
    }
}
