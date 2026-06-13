from flask import Flask, request, jsonify
from flask_cors import CORS
import pandas as pd
import numpy as np
import joblib
import os

app = Flask(__name__)
CORS(app)

# Charger le modèle
model = None
le_etat = None
le_type = None

def load_model():
    global model, le_etat, le_type
    try:
        model = joblib.load('models/panne_model.joblib')
        le_etat = joblib.load('models/label_encoder_etat.joblib')
        le_type = joblib.load('models/label_encoder_type.joblib')
        print("✅ Modèle IA chargé avec succès")
        return True
    except Exception as e:
        print(f"❌ Erreur: {e}")
        return False

@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.json
        
        # Créer le DataFrame avec TOUTES les colonnes dans le bon ordre
        df_input = pd.DataFrame([{
            'age_ans': data.get('age_ans', 0),
            'duree_vie': data.get('duree_vie', 0),
            'pourcentage_vie': data.get('pourcentage_vie', 0),
            'nb_maintenances': data.get('nb_maintenances', 0),
            'valeur': data.get('valeur', 0),
            'etat': le_etat.transform([data.get('etat', 'Bon')])[0],
            'type_ressource': le_type.transform([data.get('type_ressource', 'Autre')])[0]
        }])
        
        # Colonnes dans le bon ordre
        colonnes_ordre = ['age_ans', 'duree_vie', 'pourcentage_vie', 'nb_maintenances', 'valeur', 'etat', 'type_ressource']
        df_input = df_input[colonnes_ordre]
        
        # Prédire
        proba = model.predict_proba(df_input)[0]
        risque = float(proba[1] * 100)
        
        # Niveau de risque
        if risque >= 80:
            niveau, couleur, icone, jours, recommandation = 'Critique', '#ef4444', '🔴', 30, 'REMPLACEMENT IMMÉDIAT'
        elif risque >= 60:
            niveau, couleur, icone, jours, recommandation = 'Élevé', '#f97316', '🟠', 90, 'REMPLACEMENT PROGRAMMÉ'
        elif risque >= 40:
            niveau, couleur, icone, jours, recommandation = 'Moyen', '#f59e0b', '🟡', 180, 'MAINTENANCE PRÉVENTIVE'
        elif risque >= 20:
            niveau, couleur, icone, jours, recommandation = 'Faible', '#10b981', '🟢', 365, 'SURVEILLANCE NORMALE'
        else:
            niveau, couleur, icone, jours, recommandation = 'Très faible', '#22c55e', '✅', 730, 'ENTRETIEN COURANT'
        
        return jsonify({
            'success': True,
            'risque': risque,
            'niveau': niveau,
            'couleur': couleur,
            'icone': icone,
            'jours_restants': jours,
            'recommandation': recommandation,
            'confiance': float(max(proba))
        })
        
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/health', methods=['GET'])
def health():
    return jsonify({'status': 'ok', 'model_loaded': model is not None})



# ========== NOUVELLE ROUTE POUR L'ESTIMATION DU COÛT ==========
@app.route('/predict-cost', methods=['POST'])
def predict_cost():
    try:
        data = request.json
        
        type_maintenance = data.get('type_maintenance', 'Préventive')
        type_ressource = data.get('type_ressource', 'Outillage')
        age = data.get('age', 0)
        duree_vie = data.get('duree_vie', 10)
        nb_maintenances = data.get('nb_maintenances', 0)
        valeur = data.get('valeur', 1000)
        
        # Base selon le type de maintenance
        if type_maintenance == 'Corrective':
            cout_base = 350
        elif type_maintenance == 'Curative':
            cout_base = 450
        elif type_maintenance == 'Préventive':
            cout_base = 150
        elif type_maintenance == 'Évolutive':
            cout_base = 300
        elif type_maintenance == 'Conditionnelle':
            cout_base = 200
        elif type_maintenance == 'Inspection':
            cout_base = 80
        elif type_maintenance == 'Lubrification':
            cout_base = 100
        else:
            cout_base = 120
        
        # Coefficient selon le type de ressource
        if type_ressource == 'Camion':
            cout_base = cout_base * 1.5
        elif type_ressource == 'Engin':
            cout_base = cout_base * 1.3
        elif type_ressource == 'Outillage':
            cout_base = cout_base * 1.0
        
        # Coefficient selon l'âge
        pourcentage_vie = (age / duree_vie) * 100 if duree_vie > 0 else 0
        if pourcentage_vie > 80:
            cout_base = cout_base * 1.4
        elif pourcentage_vie > 50:
            cout_base = cout_base * 1.2
        
        # Coefficient selon la valeur
        if valeur > 10000:
            cout_base = cout_base * 1.5
        elif valeur > 5000:
            cout_base = cout_base * 1.2
        
        # Coefficient selon le nombre de maintenances
        if nb_maintenances > 10:
            cout_base = cout_base * 1.3
        elif nb_maintenances > 5:
            cout_base = cout_base * 1.15
        
        cout_estime = round(cout_base)
        min_prix = round(cout_base * 0.8)
        max_prix = round(cout_base * 1.2)
        
        # Niveau de confiance
        if nb_maintenances > 3:
            confiance = "Élevée"
        elif nb_maintenances > 0:
            confiance = "Moyenne"
        else:
            confiance = "Faible"
        
        return jsonify({
            'success': True,
            'cout_estime': cout_estime,
            'fourchette': f"{min_prix}€ - {max_prix}€",
            'confiance': confiance,
            'details': {
                'type_maintenance': type_maintenance,
                'type_ressource': type_ressource,
                'age_ans': round(age, 1),
                'pourcentage_vie': round(pourcentage_vie, 1),
                'nb_maintenances': nb_maintenances,
                'valeur': valeur
            }
        })
        
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500
# ========== FIN NOUVELLE ROUTE ==========











# ========== NOUVELLE ROUTE POUR PRÉDIRE À PARTIR DE LA DESCRIPTION ==========
@app.route('/predict-from-description', methods=['POST'])
def predict_from_description():
    try:
        data = request.json
        description = data.get('description', '').lower()
        
        print(f"📝 Analyse de la description: {description}")
        
        # Dictionnaire des mots-clés et prédictions
        mots_cles = {
            # Vidanges (Préventive)
            'vidange': {'duree_heures': 0.5, 'type': 'Préventive', 'mots': ['vidange', 'huile', 'filtre à huile']},
            'graissage': {'duree_heures': 0.25, 'type': 'Préventive', 'mots': ['graissage', 'lubrification']},
            
            # Pneus (Corrective)
            'pneu': {'duree_heures': 1, 'type': 'Corrective', 'mots': ['pneu', 'crevé', 'roue', 'gomme']},
            
            # Moteur (Corrective)
            'moteur': {'duree_heures': 4, 'type': 'Corrective', 'mots': ['moteur', 'claque', 'courroie', 'distribut']},
            
            # Freins (Corrective)
            'frein': {'duree_heures': 2, 'type': 'Corrective', 'mots': ['frein', 'plaquette', 'disque']},
            
            # Remplacement complet (Curative)
            'remplacement': {'duree_heures': 6, 'type': 'Curative', 'mots': ['remplacer', 'changer complet', 'neuf']},
            
            # Amélioration (Évolutive)
            'upgrade': {'duree_heures': 2, 'type': 'Évolutive', 'mots': ['upgrade', 'amélioration', 'mise à jour']},
        }
        
        # Taux horaire fixe
        TAUX_HORAIRE = 25
        
        # Primes par type
        PRIMES = {
            'Préventive': 0,
            'Corrective': 50,
            'Curative': 75,
            'Évolutive': 30
        }
        
        # Par défaut
        duree_heures = 2
        type_intervention = 'Corrective'
        mot_trouve = None
        
        # Rechercher les mots-clés
        for key, valeur in mots_cles.items():
            for mot in valeur['mots']:
                if mot in description:
                    duree_heures = valeur['duree_heures']
                    type_intervention = valeur['type']
                    mot_trouve = mot
                    print(f"🔍 Mot-clé trouvé: '{mot}' → {type_intervention}, {duree_heures}h")
                    break
            if mot_trouve:
                break
        
        # Calculs
        cout_main_oeuvre = duree_heures * TAUX_HORAIRE
        prime = PRIMES[type_intervention]
        cout_total = cout_main_oeuvre + prime
        
        # Formatage de la durée
        if duree_heures < 1:
            minutes = int(duree_heures * 60)
            duree_formatee = f"{minutes} minutes"
        elif duree_heures == 1:
            duree_formatee = "1 heure"
        elif duree_heures < 24:
            duree_formatee = f"{int(duree_heures)} heures"
        else:
            jours = int(duree_heures / 24)
            duree_formatee = f"{jours} jour(s)"
        
        resultat = {
            'success': True,
            'description': description,
            'duree_heures': duree_heures,
            'duree_formatee': duree_formatee,
            'type_intervention': type_intervention,
            'cout_main_oeuvre': cout_main_oeuvre,
            'prime': prime,
            'cout_total': cout_total,
            'taux_horaire': TAUX_HORAIRE,
            'mot_cle_trouve': mot_trouve
        }
        
        print(f"✅ Prédiction: {resultat}")
        return jsonify(resultat)
        
    except Exception as e:
        print(f"❌ Erreur: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500



































































if __name__ == '__main__':
    load_model()
    print("🚀 Service IA démarré sur http://localhost:5001")
    app.run(host='0.0.0.0', port=5001, debug=True)