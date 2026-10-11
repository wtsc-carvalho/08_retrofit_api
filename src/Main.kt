// ==========================================
// Prática 1: Buscando Piadas da Internet (Com Exercícios)
// ==========================================
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class Piada(
    @SerializedName("id") val id: Int,
    @SerializedName("type") val tipo: String,
    @SerializedName("setup") val pergunta: String,
    @SerializedName("punchline") val resposta: String
)

interface PiadaService {
    @GET("random_joke")
    suspend fun buscarPiadaAleatoria(): Piada

    @GET("jokes/ten")
    suspend fun buscarDezPiadas(): List<Piada> // Exercício 3: Múltiplas piadas
}

object RetrofitClient {
    private const val BASE_URL = "https://official-joke-api.appspot.com/"
    val piadaService: PiadaService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PiadaService::class.java)
    }
}

sealed interface PiadaUiState {
    data object Carregando : PiadaUiState
    data class Sucesso(
        val piadaAtual: Piada, 
        val historico: List<Piada> // Exercício 1
    ) : PiadaUiState
    data class Erro(val mensagem: String) : PiadaUiState
}

class PiadaViewModel(private val service: PiadaService = RetrofitClient.piadaService) : ViewModel() {
    private val _uiState = MutableStateFlow<PiadaUiState>(PiadaUiState.Carregando)
    val uiState: StateFlow<PiadaUiState> = _uiState.asStateFlow()
    
    private val historicoCompleto = mutableListOf<Piada>()
    
    // Exercício 3: Variáveis para navegação local de 10 piadas
    private var loteDezPiadas = emptyList<Piada>()
    private var indiceAtual = 0

    init { buscarLoteDezPiadas() }

    // Busca as 10 de vez (Exercício 3)
    fun buscarLoteDezPiadas() {
        viewModelScope.launch {
            _uiState.value = PiadaUiState.Carregando
            try {
                loteDezPiadas = service.buscarDezPiadas()
                indiceAtual = 0
                exibirPiadaAtual()
            } catch (e: Exception) {
                _uiState.value = PiadaUiState.Erro("Falha ao carregar lote: ${e.message}")
            }
        }
    }
    
    // Mostra a próxima piada sem rede
    fun proximaPiadaLocal() {
        if (indiceAtual < loteDezPiadas.size - 1) {
            indiceAtual++
            exibirPiadaAtual()
        } else {
            // Se acabou o lote, busca mais 10
            buscarLoteDezPiadas()
        }
    }
    
    private fun exibirPiadaAtual() {
        val piada = loteDezPiadas[indiceAtual]
        historicoCompleto.add(0, piada)
        val historicoAparado = historicoCompleto.take(5) // Exercício 1: Histórico limitado
        _uiState.value = PiadaUiState.Sucesso(piada, historicoAparado)
    }
}

@Composable
fun PiadaScreen(viewModel: PiadaViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var revelarResposta by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(uiState) { revelarResposta = false }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("😂 Piada do Momento", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        when (val state = uiState) {
            is PiadaUiState.Carregando -> CircularProgressIndicator()
            is PiadaUiState.Erro -> {
                Text(state.mensagem, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.buscarLoteDezPiadas() }) { Text("Tentar novamente") }
            }
            is PiadaUiState.Sucesso -> {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.piadaAtual.pergunta, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                        
                        AnimatedVisibility(visible = revelarResposta) {
                            Text(state.piadaAtual.resposta, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                        }

                        if (!revelarResposta) {
                            OutlinedButton(onClick = { revelarResposta = true }) { Text("Revelar resposta 🎭") }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.proximaPiadaLocal() }, modifier = Modifier.weight(1f)) {
                        Text("Próxima piada ➡")
                    }
                    // Exercício 2: Botão compartilhar
                    OutlinedButton(onClick = { compartilharPiada(context, state.piadaAtual) }) {
                        Text("Compartilhar")
                    }
                }
                
                // Exercício 1: Histórico exibido na UI
                Spacer(Modifier.height(24.dp))
                Text("Últimas lidas:", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.historico.forEach { p ->
                        Text("- ${p.pergunta}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

// Lógica para compartilhar (Exercício 2)
fun compartilharPiada(context: Context, piada: Piada) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, "${piada.pergunta}\n\n${piada.resposta}")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Compartilhar piada via...")
    context.startActivity(shareIntent)
}


// ==========================================
// Prática 2: Lista de Posts com JSONPlaceholder (Com Exercícios)
// ==========================================
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

data class PostDto(
    @SerializedName("id") val id: Int,
    @SerializedName("userId") val userId: Int,
    @SerializedName("title") val titulo: String,
    @SerializedName("body") val corpo: String
)

data class Post(val id: Int, val titulo: String, val corpo: String)
fun PostDto.toDomain() = Post(id = id, titulo = titulo, corpo = corpo)

interface PostService {
    @GET("posts")
    suspend fun buscarPosts(): List<PostDto>
}

class PostRepository(private val service: PostService) {
    suspend fun buscarPosts(): List<Post> = service.buscarPosts().map { it.toDomain() }
}

sealed interface PostListUiState {
    data object Carregando : PostListUiState
    data class Sucesso(val posts: List<Post>) : PostListUiState
    data class Erro(val mensagem: String) : PostListUiState
}

class PostListViewModel(private val repository: PostRepository) : ViewModel() {
    private val _uiState = MutableStateFlow<PostListUiState>(PostListUiState.Carregando)
    val uiState: StateFlow<PostListUiState> = _uiState.asStateFlow()
    
    // Controle do Exercício 3: Paginação manual
    private var todosOsPosts = emptyList<Post>()
    val quantidadeVisivel = MutableStateFlow(10)

    init { carregarPosts() }

    fun carregarPosts() {
        viewModelScope.launch {
            _uiState.value = PostListUiState.Carregando
            try {
                todosOsPosts = repository.buscarPosts()
                _uiState.value = PostListUiState.Sucesso(todosOsPosts)
            } catch (e: Exception) {
                _uiState.value = PostListUiState.Erro(e.message ?: "Erro ao carregar")
            }
        }
    }
    
    fun carregarMais() {
        quantidadeVisivel.update { it + 10 }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostListScreen(viewModel: PostListViewModel, onPostClick: (Post) -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val qtdVisivel by viewModel.quantidadeVisivel.collectAsStateWithLifecycle()
    
    var textoPesquisa by remember { mutableStateOf("") } // Exercício 1: Estado da busca

    Scaffold(
        topBar = { 
            Column {
                TopAppBar(title = { Text("Posts") }) 
                OutlinedTextField(
                    value = textoPesquisa,
                    onValueChange = { textoPesquisa = it },
                    placeholder = { Text("Pesquisar título...") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is PostListUiState.Carregando -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is PostListUiState.Erro -> {
                    Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❌ ${state.mensagem}", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.carregarPosts() }) { Text("Tentar novamente") }
                    }
                }
                is PostListUiState.Sucesso -> {
                    // Exercício 1 (Filtro) e Exercício 3 (Take/Paginação) encadeados
                    val filtrados = state.posts
                        .filter { it.titulo.contains(textoPesquisa, ignoreCase = true) }
                        .take(qtdVisivel)
                        
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filtrados, key = { it.id }) { post ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onPostClick(post) }) { // Exercício 2
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("${post.id}. ${post.titulo}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.height(4.dp))
                                    Text(post.corpo, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                            }
                        }
                        
                        // Botão de paginação (Exercício 3)
                        item {
                            if (qtdVisivel < state.posts.size && textoPesquisa.isEmpty()) {
                                OutlinedButton(onClick = { viewModel.carregarMais() }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                                    Text("Carregar mais...")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ==========================================
// Prática 3: Tratamento de Erros e Retry (Exercícios)
// ==========================================
import retrofit2.HttpException
import java.io.IOException
import kotlinx.coroutines.delay

enum class TipoErroUI { REDE, SERVIDOR, DESCONHECIDO }

data class ErroTratado(val mensagem: String, val tipo: TipoErroUI)

suspend fun buscarComTratamentoERetry(service: PiadaService): Result<Piada> {
    var ultimaExcecao: Exception? = null
    
    // Exercício 3: Retry com backoff
    repeat(3) { tentativa ->
        try {
            val piada = service.buscarPiadaAleatoria()
            return Result.success(piada)
        } catch (e: Exception) {
            ultimaExcecao = e
            delay(1000L * (tentativa + 1)) // Backoff: 1s, 2s, 3s...
        }
    }
    
    // Mapeamento (Exercícios 1 e 2)
    return when (ultimaExcecao) {
        is IOException -> Result.failure(Exception("REDE|Sem conexão com a internet."))
        is HttpException -> Result.failure(Exception("SERVIDOR|Erro do servidor HTTP ${(ultimaExcecao as HttpException).code()}."))
        else -> Result.failure(Exception("DESCONHECIDO|Erro inesperado: ${ultimaExcecao?.message}"))
    }
}

// Demonstração no ViewModel com Result
class RetryPiadaViewModel(private val service: PiadaService = RetrofitClient.piadaService) : ViewModel() {
    private val _uiState = MutableStateFlow<PiadaUiState>(PiadaUiState.Carregando)
    val uiState: StateFlow<PiadaUiState> = _uiState.asStateFlow()
    
    // Adição para o Exercício 2 (guardar tipo de erro)
    var ultimoTipoErro: TipoErroUI? = null

    init { buscarPiadaResiliente() }

    fun buscarPiadaResiliente() {
        viewModelScope.launch {
            _uiState.value = PiadaUiState.Carregando
            
            val resultado = buscarComTratamentoERetry(service)
            resultado.onSuccess { piada ->
                ultimoTipoErro = null
                _uiState.value = PiadaUiState.Sucesso(piada, emptyList())
            }.onFailure { erro ->
                val partes = erro.message?.split("|")
                if(partes != null && partes.size == 2) {
                    ultimoTipoErro = TipoErroUI.valueOf(partes[0])
                    _uiState.value = PiadaUiState.Erro(partes[1])
                } else {
                    ultimoTipoErro = TipoErroUI.DESCONHECIDO
                    _uiState.value = PiadaUiState.Erro(erro.message ?: "Erro genérico")
                }
            }
        }
    }
}