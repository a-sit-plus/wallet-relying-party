package at.asit.wallet.relyingparty

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverters
import org.springframework.http.converter.json.KotlinSerializationJsonHttpMessageConverter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebMvcConfiguration : WebMvcConfigurer {
    override fun configureMessageConverters(builder: HttpMessageConverters.ServerBuilder) {
        builder.withKotlinSerializationJsonConverter(KotlinSerializationJsonHttpMessageConverter(joseCompliantSerializer))
    }
}
