package backend.db

import sklepsearch.*
import java.sql.Connection
import javax.sql.DataSource

class PostgresMallRepository(private val ds: DataSource) : MallRepository {

    override fun isEmpty(): Boolean = ds.connection.use { c ->
        c.createStatement().use { st -> st.executeQuery("SELECT NOT EXISTS (SELECT 1 FROM malls)").use { it.next(); it.getBoolean(1) } }
    }

    override fun findAll(): List<Mall> = ds.connection.use { c ->
        val ids = c.prepareStatement("SELECT id FROM malls ORDER BY id").use { ps ->
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong(1)) } }
        }
        ids.mapNotNull { load(c, it) }
    }

    override fun findById(id: Long): Mall? = ds.connection.use { load(it, id) }

    private fun load(c: Connection, id: Long): Mall? {
        val head = c.prepareStatement(
            "SELECT name, size_x, size_y, ul_lat, ul_lon, dr_lat, dr_lon, min_floor, outline FROM malls WHERE id = ?"
        ).use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs ->
                if (!rs.next()) return null
                Head(
                    rs.getString(1), Size(rs.getInt(2), rs.getInt(3)),
                    GeoPoint(longitude = rs.getDouble(5), latitude = rs.getDouble(4)),
                    GeoPoint(longitude = rs.getDouble(7), latitude = rs.getDouble(6)),
                    rs.getInt(8), rs.getString(9)?.let { Path2D.fromSvgPath(it) }
                )
            }
        }

        val entries = query(c, "SELECT x, y, name FROM mall_entry_points WHERE mall_id = ? ORDER BY idx", id) {
            Point(it.getDouble(1), it.getDouble(2)) to it.getString(3)
        }

        val storeEntries = HashMap<Long, MutableList<Point>>()
        query(
            c,
            """SELECT e.store_id, e.x, e.y FROM store_entry_points e
               JOIN stores s ON s.instance_id = e.store_id WHERE s.mall_id = ? ORDER BY e.store_id, e.idx""",
            id
        ) { storeEntries.getOrPut(it.getLong(1)) { mutableListOf() }.add(Point(it.getDouble(2), it.getDouble(3))) }

        val stores = query(
            c,
            """SELECT floor_number, instance_id, shop_id, name, category, description, area
               FROM stores WHERE mall_id = ? ORDER BY floor_number, instance_id""",
            id
        ) {
            val instanceId = it.getLong(2)
            it.getInt(1) to Store(
                Instanceid = instanceId, Shopid = it.getLong(3), name = it.getString(4),
                area = Path2D.fromSvgPath(it.getString(7)),
                entryPoints = storeEntries[instanceId].orEmpty(),
                category = it.getString(5), description = it.getString(6)
            )
        }.groupBy({ it.first }, { it.second })

        val elevators = query(c, "SELECT floor_number, id, x, y, is_accessible FROM elevators WHERE mall_id = ? ORDER BY floor_number, id", id) {
            it.getInt(1) to Elevator(it.getLong(2), Point(it.getDouble(3), it.getDouble(4)), it.getBoolean(5))
        }.groupBy({ it.first }, { it.second })

        val escalators = query(c, "SELECT floor_number, id, x, y, direction, is_accessible FROM escalators WHERE mall_id = ? ORDER BY floor_number, id", id) {
            it.getInt(1) to Escalator(
                it.getLong(2), Point(it.getDouble(3), it.getDouble(4)),
                EscalatorDirection.valueOf(it.getString(5)), it.getBoolean(6)
            )
        }.groupBy({ it.first }, { it.second })

        val voids = query(c, "SELECT floor_number, outline FROM floor_voids WHERE mall_id = ? ORDER BY floor_number, idx", id) {
            it.getInt(1) to Path2D.fromSvgPath(it.getString(2))
        }.groupBy({ it.first }, { it.second })

        val floors = query(c, "SELECT number, outline FROM floors WHERE mall_id = ? ORDER BY number", id) {
            val n = it.getInt(1)
            Floor(
                number = n, box = Path2D.fromSvgPath(it.getString(2)),
                stores = stores[n].orEmpty(), elevators = elevators[n].orEmpty(), escalators = escalators[n].orEmpty(),
                voids = voids[n].orEmpty()
            )
        }

        return Mall(id, head.name, head.size, head.ul, head.dr, head.minFloor, entries.map { it.first }, entries.map { it.second }, floors, head.outline)
    }

    override fun save(mall: Mall) {
        ds.connection.use { c ->
            c.autoCommit = false
            try {
                c.prepareStatement("DELETE FROM malls WHERE id = ?").use { it.setLong(1, mall.id); it.executeUpdate() }
                insert(c, mall)
                c.commit()
            } catch (e: Throwable) {
                c.rollback()
                throw e
            }
        }
    }

    override fun nextMallId(): Long = scalar("SELECT COALESCE(MAX(id), 0) + 1 FROM malls")

    override fun nextStoreInstanceId(): Long = scalar("SELECT COALESCE(MAX(instance_id), 0) + 1 FROM stores")

    private fun scalar(sql: String): Long = ds.connection.use { c ->
        c.createStatement().use { st -> st.executeQuery(sql).use { it.next(); it.getLong(1) } }
    }

    override fun delete(id: Long): Boolean = ds.connection.use { c ->
        c.prepareStatement("DELETE FROM malls WHERE id = ?").use { it.setLong(1, id); it.executeUpdate() > 0 }
    }

    private fun insert(c: Connection, mall: Mall) {
        c.prepareStatement("INSERT INTO malls (id, name, size_x, size_y, ul_lat, ul_lon, dr_lat, dr_lon, min_floor, outline) VALUES (?,?,?,?,?,?,?,?,?,?)").use {
            it.setLong(1, mall.id); it.setString(2, mall.name)
            it.setInt(3, mall.size.x); it.setInt(4, mall.size.y)
            it.setDouble(5, mall.upperLeft.latitude); it.setDouble(6, mall.upperLeft.longitude)
            it.setDouble(7, mall.downRight.latitude); it.setDouble(8, mall.downRight.longitude)
            it.setInt(9, mall.minFloor)
            it.setString(10, mall.outline?.toSvgPath())
            it.executeUpdate()
        }
        mall.entryPoints.forEachIndexed { i, p ->
            c.prepareStatement("INSERT INTO mall_entry_points (mall_id, idx, x, y, name) VALUES (?,?,?,?,?)").use {
                it.setLong(1, mall.id); it.setInt(2, i); it.setDouble(3, p.x); it.setDouble(4, p.y)
                it.setString(5, mall.entryPointNames.getOrNull(i) ?: "Entrance / Exit #${i + 1}")
                it.executeUpdate()
            }
        }
        for (f in mall.floors) {
            c.prepareStatement("INSERT INTO floors VALUES (?,?,?)").use {
                it.setLong(1, mall.id); it.setInt(2, f.number); it.setString(3, f.box.toSvgPath()); it.executeUpdate()
            }
            for (s in f.stores) {
                c.prepareStatement("INSERT INTO stores VALUES (?,?,?,?,?,?,?,?)").use {
                    it.setLong(1, s.Instanceid); it.setLong(2, mall.id); it.setInt(3, f.number)
                    it.setLong(4, s.Shopid); it.setString(5, s.name); it.setString(6, s.category)
                    it.setString(7, s.description); it.setString(8, s.area.toSvgPath())
                    it.executeUpdate()
                }
                s.entryPoints.forEachIndexed { i, p ->
                    c.prepareStatement("INSERT INTO store_entry_points VALUES (?,?,?,?)").use {
                        it.setLong(1, s.Instanceid); it.setInt(2, i); it.setDouble(3, p.x); it.setDouble(4, p.y); it.executeUpdate()
                    }
                }
            }
            for (e in f.elevators) {
                c.prepareStatement("INSERT INTO elevators (mall_id, floor_number, id, x, y, is_accessible) VALUES (?,?,?,?,?,?)").use {
                    it.setLong(1, mall.id); it.setInt(2, f.number); it.setLong(3, e.id)
                    it.setDouble(4, e.coordinates.x); it.setDouble(5, e.coordinates.y)
                    it.setBoolean(6, e.isAccessible); it.executeUpdate()
                }
            }
            for (e in f.escalators) {
                c.prepareStatement("INSERT INTO escalators (mall_id, floor_number, id, x, y, direction, is_accessible) VALUES (?,?,?,?,?,?,?)").use {
                    it.setLong(1, mall.id); it.setInt(2, f.number); it.setLong(3, e.id)
                    it.setDouble(4, e.coordinates.x); it.setDouble(5, e.coordinates.y); it.setString(6, e.direction.name)
                    it.setBoolean(7, e.isAccessible)
                    it.executeUpdate()
                }
            }
            f.voids.forEachIndexed { i, void ->
                c.prepareStatement("INSERT INTO floor_voids (mall_id, floor_number, idx, outline) VALUES (?,?,?,?)").use {
                    it.setLong(1, mall.id); it.setInt(2, f.number); it.setInt(3, i)
                    it.setString(4, void.toSvgPath()); it.executeUpdate()
                }
            }
        }
    }

    private class Head(val name: String, val size: Size, val ul: GeoPoint, val dr: GeoPoint, val minFloor: Int, val outline: Path2D?)

    private inline fun <T> query(c: Connection, sql: String, mallId: Long, map: (java.sql.ResultSet) -> T): List<T> =
        c.prepareStatement(sql).use { ps ->
            ps.setLong(1, mallId)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
        }
}
