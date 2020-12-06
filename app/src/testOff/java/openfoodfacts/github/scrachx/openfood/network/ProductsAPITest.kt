package openfoodfacts.github.scrachx.openfood.network

import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import openfoodfacts.github.scrachx.openfood.models.Search
import openfoodfacts.github.scrachx.openfood.models.entities.SendProduct
import openfoodfacts.github.scrachx.openfood.network.services.ProductsAPI
import openfoodfacts.github.scrachx.openfood.utils.Utils
import openfoodfacts.github.scrachx.openfood.utils.getUserAgent
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import java.time.Duration

class ProductsAPITest {
    @Test
    fun byLanguage() {
        val search = prodClient.getProductsByLanguage("italian").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search!!.products).isNotNull()
    }

    @Test
    fun byLabel() {
        val search = prodClient.getProductsByLabel("utz-certified").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search!!.products).isNotNull()
    }

    @Test
    fun byCategory() {
        val search = prodClient.getProductsByCategory("baby-foods").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search!!.products).isNotNull()
    }

    @Test
    fun byState() {
        val fieldsToFetchFacets = "brands,product_name,image_small_url,quantity,nutrition_grades_tags"
        val search = prodClient.getProductsByState("complete", fieldsToFetchFacets).blockingGet()
        assertThat(search).isNotNull()
        assertThat(search!!.products).isNotNull()
    }

    @Test
    fun byPackaging() {
        val search = prodClient.getProductsByPackaging("cardboard").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search.products).isNotNull()
    }

    @Test
    fun byBrand() {
        val search = prodClient.getProductsByBrand("monoprix").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search.products).isNotNull()
    }

    @Test
    fun byPurchasePlace() {
        val search = prodClient.getProductsByPurchasePlace("marseille-5").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search.products).isNotNull()
    }

    @Test
    fun byStore() {
        val search = prodClient.getProductsByStore("super-u").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search!!.products).isNotNull()
    }

    @Test
    fun byCountry() {
        val search = prodClient.byCountry("france").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search.products).isNotEmpty()
    }

    @Test
    fun byIngredient() {
        val search = prodClient.getProductsByIngredient("sucre").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search.products).isNotEmpty()
    }

    @Test
    fun byTrace() {
        val search = prodClient.getProductsByTrace("eggs").blockingGet()
        assertThat(search).isNotNull()
        assertThat(search.products).isNotNull()
    }

    @Test
    fun productByTrace_eggs_productsFound() {
        val response = prodClient.getProductsByTrace("eggs").blockingGet()
        assertProductsFound(response)
    }

    @Test
    fun productByPackagerCode_emb35069c_productsFound() {
        val response = prodClient.byPackagerCode("emb-35069c").blockingGet()
        assertProductsFound(response)
    }

    @Test
    fun productByNutritionGrade_a_productsFound() {
        val res = prodClient.byNutritionGrade("a").blockingGet()
        assertProductsFound(res)
    }

    @Test
    fun productByCity_Paris_noProductFound() {
        val response = prodClient.byCity("paris").blockingGet()
        assertNoProductsFound(response)
    }

    @Test
    fun productByAdditive_e301_productsFound() {
        val fieldsToFetchFacets = "brands,product_name,image_small_url,quantity,nutrition_grades_tags"
        val response = prodClient.getProductsByAdditive("e301-sodium-ascorbate", fieldsToFetchFacets).blockingGet()
        assertProductsFound(response)
    }

    @Test
    fun product_notFound() {
        val barcode = "457457457"
        prodClient.getProductByBarcodeSingle(
                barcode,
                "code",
                getUserAgent(Utils.HEADER_USER_AGENT_SEARCH)
        ).subscribe({ productState ->
            assertThat(productState.status).isEqualTo(0)
            assertThat(productState.statusVerbose).isEqualTo("product not found")
            assertThat(productState.code).isEqualTo(barcode)
        }) {
            fail("Request returned error")
            it.printStackTrace()
        }
    }

    @Test
    fun post_product() {
        val product = SendProduct().apply {
            barcode = "1234567890"
            name = "ProductName"
            brands = "productbrand"
            weight = "123"
            weight_unit = "g"
            lang = "en"
        }

        val productDetails = mapOf<String?, String?>(
                "lang" to product.lang,
                "product_name" to product.name,
                "brands" to product.brands,
                "quantity" to product.quantity
        )


        val body = devClientWithAuth
                .saveProductSingle(product.barcode, productDetails, OpenFoodAPIClient.commentToUpload)
                .blockingGet()
        assertThat(body.status).isEqualTo(1)
        assertThat(body.statusVerbose).isEqualTo("fields saved")
        val fields = "product_name,brands,brands_tags,quantity"
        val response = devClientWithAuth.getProductByBarcodeSingle(
                product.barcode,
                fields,
                getUserAgent(Utils.HEADER_USER_AGENT_SEARCH)
        ).blockingGet()

        val savedProduct = response!!.product!!
        assertThat(savedProduct.productName).isEqualTo(product.name)
        assertThat(savedProduct.brands).isEqualTo(product.brands)
        assertThat(savedProduct.brandsTags).contains(product.brands)
        assertThat(savedProduct.quantity).isEqualTo("${product.weight} ${product.weight_unit}")
    }

    companion object {
        /**
         * We need to use auth because we use world.openfoodfacts.dev
         */
        private lateinit var devClientWithAuth: ProductsAPI
        private lateinit var prodClient: ProductsAPI
        private const val DEV_API = "https://world.openfoodfacts.dev"

        @BeforeClass
        @JvmStatic
        fun setupClient() {
            val httpClientWithAuth = OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                    .connectTimeout(Duration.ZERO)
                    .readTimeout(Duration.ZERO)
                    .addInterceptor {
                        val origReq = it.request()
                        it.proceed(origReq.newBuilder()
                                .header("Authorization", "Basic b2ZmOm9mZg==")
                                .header("Accept", "application/json")
                                .method(origReq.method(), origReq.body()).build())
                    }
                    .build()
            prodClient = CommonApiManager.productsApi
            devClientWithAuth = Retrofit.Builder()
                    .baseUrl(DEV_API)
                    .addConverterFactory(JacksonConverterFactory.create())
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .client(httpClientWithAuth)
                    .build()
                    .create(ProductsAPI::class.java)
        }

        private fun assertProductsFound(search: Search) {
            val products = search.products
            assertThat(products).isNotNull()
            assertThat(search.count.toInt()).isGreaterThan(0)
            assertThat(products.isEmpty()).isFalse()
        }

        private fun assertNoProductsFound(search: Search) {
            assertThat(search).isNotNull()
            val products = search.products
            assertThat(products.isEmpty()).isTrue()
            assertThat(search.count.toInt()).isEqualTo(0)
        }
    }
}