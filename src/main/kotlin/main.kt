import com.google.gson.JsonParser
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess

suspend fun main(args: Array<String>) {
    // two arguments must be provided, the username and the keyword
    if(args.size != 2) {
        println("2 arguments should be provided, first should be username, second should be keyword")
        exitProcess(1)
    }

    val username = args[0]
    val keyword = args[1]
    val limit = 100
    var after: String? = ""
    val comments = mutableListOf<Map<String, Any>>()

    println("Searching comments of '$username' for keyword '$keyword'")

    val client = HttpClient()
    // reddit returns comments in batches of (max) 100, so we loop until no more comments are left
    do {
        withContext(Dispatchers.Default) {
            val response: String = client.get("https://www.reddit.com/user/${username}/comments.json?limit=${limit}&after=${after}")
            val json = JsonParser.parseString(response).asJsonObject
            // actual information is in 'data' key
            val data = json.getAsJsonObject("data")
            // after is to be used as an anchor point for the pagination, when it is null this is the last page
            after = data.get("after").let { if(it.isJsonNull) null else it.asString}
            // get the actual comments (children)
            val children = data.getAsJsonArray("children")
            // construct a map that contains the body (the text), the time and the parent (the thing we commented on) for each comment
            val newComments = children.map{ child -> child.asJsonObject.getAsJsonObject("data").let {
                HashMap<String, Any>().apply {
                    put("body", it.get("body").asString)
                    put("time", it.get("created_utc").asLong)
                    put("parent", it.get("link_permalink").asString)
                }
            } }
            // add the comments from this page to the list of all comments so far
            comments += newComments
            print("\rFetched ${comments.size} comments")
        }
    } while(after != null)
    println()

    println("Searching ${comments.size} comments for keyword '$keyword'")

    // filter the comments by whether their body (text) contains the keyword
    val matches = comments.filter { (it.getValue("body") as String).contains(keyword, ignoreCase = true) }
    println("${matches.size} matching comments:\n${matches.joinToString("\n")}")

    // close the httpclient
    client.close()
}