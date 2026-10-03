package backend.data

import backend.db.MallRepository
import sklepsearch.Mall

/** In-memory snapshot of the galleries; call [reload] after the database content changes. */
class MallCatalog(initial: List<Mall>) {
    @Volatile
    var malls: List<Mall> = initial
        private set

    fun find(id: Long): Mall? = malls.find { it.id == id }

    fun reload(repository: MallRepository) {
        malls = repository.findAll()
    }

    companion object {
        fun load(repository: MallRepository) = MallCatalog(repository.findAll())
    }
}
