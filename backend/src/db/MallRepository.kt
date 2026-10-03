package backend.db

import sklepsearch.Mall

interface MallRepository {
    fun findAll(): List<Mall>
    fun findById(id: Long): Mall?
    fun isEmpty(): Boolean
    /** Inserts the mall, or fully replaces it if it already exists. */
    fun save(mall: Mall)
    fun nextMallId(): Long
    fun nextStoreInstanceId(): Long
    /** Returns false if there was no such mall. */
    fun delete(id: Long): Boolean
}
