# language: fr
Fonctionnalité: Passer commande (US-203)
  En tant que membre, je veux valider mon panier afin d'acheter mes produits.

  Scénario: Commande réussie
    Étant donné un produit dont le stock est 5
    Et un panier contenant 2 exemplaires de ce produit
    Quand le membre passe commande
    Alors une commande au statut "CONFIRMED" est créée
    Et le stock du produit passe à 3
    Et le panier est vidé

  Scénario: Stock insuffisant
    Étant donné un produit dont le stock est 5
    Et un panier contenant 6 exemplaires de ce produit
    Quand le membre passe commande
    Alors la réponse est 409
    Et le stock du produit reste à 5

  Scénario: Panier vide
    Étant donné un panier vide
    Quand le membre passe commande
    Alors la réponse est 400
