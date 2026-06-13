import pandas as pd
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder
import joblib
import json
import os

print("🔮 Entraînement du modèle de prédiction de panne...")

# Créer le dossier models s'il n'existe pas
os.makedirs('models', exist_ok=True)

# Générer des données d'entraînement simulées
data = []

for i in range(2000):
    age_ans = np.random.uniform(0, 15)
    duree_vie = np.random.uniform(5, 20)
    nb_maintenances = int(np.random.poisson(age_ans * 0.5))
    etat = np.random.choice(['Bon', 'Moyen', 'Mauvais'], p=[0.5, 0.3, 0.2])
    type_ressource = np.random.choice(['Camion', 'Engin', 'Outillage', 'Informatique'])
    valeur = np.random.uniform(500, 50000)
    
    pourcentage_vie = (age_ans / duree_vie) * 100
    etat_score = {'Bon': 1, 'Moyen': 2, 'Mauvais': 3}[etat]
    
    risque = (pourcentage_vie / 100) * 0.4 + (nb_maintenances / 10) * 0.3 + (etat_score / 3) * 0.3
    panne = 1 if risque > 0.55 else 0
    
    data.append({
        'age_ans': age_ans,
        'duree_vie': duree_vie,
        'pourcentage_vie': pourcentage_vie,
        'nb_maintenances': nb_maintenances,
        'etat': etat,
        'type_ressource': type_ressource,
        'valeur': valeur,
        'panne': panne
    })

df = pd.DataFrame(data)

# Préparer les features
features = ['age_ans', 'duree_vie', 'pourcentage_vie', 'nb_maintenances', 'valeur']
X = df[features].copy()

# Encoder les variables catégorielles
le_etat = LabelEncoder()
le_type = LabelEncoder()

X['etat'] = le_etat.fit_transform(df['etat'])
X['type_ressource'] = le_type.fit_transform(df['type_ressource'])

y = df['panne']

# Diviser et entraîner
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

model = RandomForestClassifier(n_estimators=100, max_depth=10, random_state=42)
model.fit(X_train, y_train)

accuracy = model.score(X_test, y_test)
print(f"✅ Précision du modèle: {accuracy * 100:.2f}%")

# Sauvegarder
joblib.dump(model, 'models/panne_model.joblib')
joblib.dump(le_etat, 'models/label_encoder_etat.joblib')
joblib.dump(le_type, 'models/label_encoder_type.joblib')

print("✅ Modèle sauvegardé dans le dossier models/")