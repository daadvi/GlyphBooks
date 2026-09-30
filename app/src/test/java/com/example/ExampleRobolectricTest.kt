package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.BookEntity
import com.example.data.model.DiscussionMessageEntity
import com.example.data.model.StudyChannelEntity
import com.example.data.model.StudyDiscussionChannelsData
import com.example.data.model.UserTasteProfile
import com.example.util.DiscussionModerationFilter
import com.example.util.DiscussionNotificationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GlyphBook", appName)
  }

  @Test
  fun `verify book entity progress calculation`() {
    val book = BookEntity(
      title = "Clean Code",
      author = "Robert C. Martin",
      pageCount = 400,
      currentPage = 200
    )
    assertEquals(0.5f, book.readingProgressPercent, 0.001f)
  }

  @Test
  fun `verify user taste profile weights genre quantity and star ratings`() {
    val books = listOf(
      BookEntity(title = "1984", author = "George Orwell", genre = "Ficção Científica", rating = 5),
      BookEntity(title = "Duna", author = "Frank Herbert", genre = "Ficção Científica", rating = 5),
      BookEntity(title = "Clean Code", author = "Robert C. Martin", genre = "Tecnologia", rating = 4),
      BookEntity(title = "Livro Desejado", author = "Autor", genre = "Fantasia", isWishlist = true)
    )

    val profile = UserTasteProfile.calculateFromBooks(books)
    assertEquals(3, profile.totalBooks)
    assertEquals(3, profile.totalRatedBooks)
    assertEquals("Ficção Científica", profile.primaryGenre)
    assertTrue(profile.topGenres.first().weightedScore > profile.topGenres[1].weightedScore)
  }

  @Test
  fun `verify database starts empty when zeroed`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    db.bookDao().deleteAllBooks()
    val initial = db.bookDao().getBookByIdDirect(1L)
    assertEquals(null, initial)
  }

  @Test
  fun `verify study discussion channels and message persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)

    assertEquals(8, StudyDiscussionChannelsData.INITIAL_SEEDS.size)

    val msg = DiscussionMessageEntity(
      bookKey = "O GÊNIO DA LÂMPADA",
      channelId = "TEORIAS",
      senderName = "Você",
      isFromUser = true,
      text = "Minha teoria sobre o enredo alienígena."
    )
    val id = db.discussionDao().insertMessage(msg)
    assertTrue(id > 0)

    val count = db.discussionDao().getMessageCount("O GÊNIO DA LÂMPADA", "TEORIAS")
    assertTrue(count >= 1)
  }

  @Test
  fun `verify custom channel creation and simultaneous 20-people filter`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)

    val bookKey = "LIVRO_TESTE"

    val openChannel = StudyChannelEntity(
      id = "open_room_1",
      bookKey = bookKey,
      title = "SALA COM VAGAS",
      description = "Discussão aberta",
      iconType = "CHAT",
      accentColorHex = "#00E676",
      currentActiveUsers = 12,
      maxSimultaneousUsers = 20
    )
    db.studyChannelDao().insertChannel(openChannel)

    val fullChannel = StudyChannelEntity(
      id = "full_room_2",
      bookKey = bookKey,
      title = "SALA LOTADA",
      description = "Capacidade máxima atingida",
      iconType = "STAR",
      accentColorHex = "#FF5722",
      currentActiveUsers = 20,
      maxSimultaneousUsers = 20
    )
    db.studyChannelDao().insertChannel(fullChannel)

    val available = db.studyChannelDao().getAvailableChannels(bookKey).first()
    assertEquals(1, available.size)
    assertEquals("open_room_1", available.first().id)
  }

  @Test
  fun `verify discussion search filters by title or theme`() {
    val channels = StudyDiscussionChannelsData.INITIAL_SEEDS

    val searchTitle = "PERSONAGENS"
    val filteredByTitle = channels.filter {
      it.title.contains(searchTitle, ignoreCase = true) || it.description.contains(searchTitle, ignoreCase = true)
    }
    assertEquals(2, filteredByTitle.size)

    val searchUnique = "CURIOSIDADES"
    val filteredUnique = channels.filter {
      it.title.contains(searchUnique, ignoreCase = true) || it.description.contains(searchUnique, ignoreCase = true)
    }
    assertEquals(1, filteredUnique.size)
    assertEquals("CURIOSIDADES", filteredUnique.first().id)

    val searchTheme = "símbolos"
    val filteredByTheme = channels.filter {
      it.title.contains(searchTheme, ignoreCase = true) || it.description.contains(searchTheme, ignoreCase = true)
    }
    assertEquals(1, filteredByTheme.size)
    assertEquals("TEORIAS", filteredByTheme.first().id)
  }

  @Test
  fun `verify moderation filter blocks interpersonal insult to user but allows book and character criticism`() {
    // 1. Direct insult to another user -> BLOCKED
    val insultResult = DiscussionModerationFilter.checkMessage("Você é um idiota e não entende nada!")
    assertFalse(insultResult.isAllowed)

    val abusiveResult = DiscussionModerationFilter.checkMessage("Cala a boca seu babaca")
    assertFalse(abusiveResult.isAllowed)

    // 2. Book criticism -> ALLOWED
    val bookCriticism = DiscussionModerationFilter.checkMessage("Achei esse livro chato e a escrita muito cansativa.")
    assertTrue(bookCriticism.isAllowed)

    // 3. Character criticism -> ALLOWED
    val characterCriticism = DiscussionModerationFilter.checkMessage("O vilão é desprezível e o personagem principal é fraco.")
    assertTrue(characterCriticism.isAllowed)

    // 4. Normal study conversation -> ALLOWED
    val normalDiscussion = DiscussionModerationFilter.checkMessage("Alguém mais notou o significado da árvore no capítulo 3?")
    assertTrue(normalDiscussion.isAllowed)
  }

  @Test
  fun `verify active room tracking in discussion notification manager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DiscussionNotificationManager(context)

    val book = "O GÊNIO DA LÂMPADA"
    val channel = "TEORIAS"

    assertFalse(manager.isRoomActiveForUser(book, channel))
    manager.markRoomEntered(book, channel)
    assertTrue(manager.isRoomActiveForUser(book, channel))
  }
}
