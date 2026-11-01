# language: fr
Fonctionnalité: Espace de troc – publication, retrait et modération des annonces
  Critères d'acceptation des user stories US-101, US-103 et US-104 du backlog.

  Scénario: Publication réussie (US-101)
    Étant donné un membre authentifié "alice"
    Quand "alice" publie une annonce "Figurine Orc édition festival" de type "EXCHANGE" dans la catégorie "FIGURINE"
    Alors la réponse est 201
    Et l'annonce a le statut "PUBLISHED"
    Et "alice" en est la propriétaire

  Scénario: Publication refusée sans authentification (US-101)
    Étant donné un internaute non authentifié
    Quand il publie une annonce
    Alors la réponse est 401

  Scénario: Le propriétaire retire son annonce (US-103)
    Étant donné une annonce publiée par "alice"
    Quand "alice" retire l'annonce
    Alors la réponse est 204
    Et l'annonce n'est plus visible publiquement

  Scénario: Un autre membre ne peut pas retirer l'annonce (US-103)
    Étant donné une annonce publiée par "alice"
    Quand "bob" retire l'annonce
    Alors la réponse est 403
    Et l'annonce a le statut "PUBLISHED"

  Scénario: Retrait par un modérateur (US-104)
    Étant donné une annonce publiée par "alice"
    Quand un modérateur "modo" retire l'annonce avec le motif "Contrefaçon"
    Alors la réponse est 200
    Et l'annonce a le statut "MODERATED"
    Et le motif "Contrefaçon" et le modérateur "modo" sont enregistrés

  Scénario: Un membre ne peut pas modérer (US-104)
    Étant donné une annonce publiée par "bob"
    Quand "alice" tente de modérer l'annonce
    Alors la réponse est 403
