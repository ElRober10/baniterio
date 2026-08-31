package com.baniterio.api.push;

import java.io.FileInputStream;
import java.io.IOException;

import com.baniterio.api.config.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inicializa el {@link FirebaseApp} a partir del fichero de cuenta de servicio
 * ({@code app.push.credenciales-json}) y expone la costura {@link EnvioMulticast}.
 * Solo se activa con {@code app.push.modo=fcm}; en {@code log} nada de esto se
 * carga y no hacen falta credenciales.
 */
@Configuration
@ConditionalOnProperty(name = "app.push.modo", havingValue = "fcm")
public class ConfiguracionFirebase {

    @Bean
    FirebaseApp firebaseApp(AppProperties props) throws IOException {
        String ruta = props.push().credencialesJson();
        if (ruta == null || ruta.isBlank()) {
            throw new IllegalStateException(
                    "app.push.modo=fcm requiere app.push.credenciales-json (ruta al service-account.json)");
        }
        try (FileInputStream credenciales = new FileInputStream(ruta)) {
            FirebaseOptions opciones = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credenciales))
                    .build();
            return FirebaseApp.getApps().isEmpty()
                    ? FirebaseApp.initializeApp(opciones)
                    : FirebaseApp.getInstance();
        }
    }

    @Bean
    EnvioMulticast envioMulticast(FirebaseApp app) {
        return FirebaseMessaging.getInstance(app)::sendEachForMulticast;
    }
}
