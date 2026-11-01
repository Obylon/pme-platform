# language: fr
Fonctionnalité: Abonnement au fanzine (US-301)
  En tant que membre, je veux souscrire ou renouveler mon abonnement au fanzine.

  Scénario: Première souscription
    Quand le membre souscrit la formule "DIGITAL"
    Alors la réponse est 200
    Et un abonnement actif est créé jusqu'à aujourd'hui plus un an

  Scénario: Renouvellement anticipé
    Quand le membre souscrit la formule "PAPER"
    Et le membre souscrit la formule "BOTH"
    Alors la date de fin est prolongée d'un an supplémentaire
