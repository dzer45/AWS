Mon architecture est défini de la façon suivante :
	- une class Java qui écoute sur le port 8080 et qui gère la répartition
du travail avec la création d'un worker à chaque fois que la taille de la queue de 
requête comporte un multiple de 25 et diminnue le nombre de Worker à chaque fois 
qu'on dimminue de 25 la taille de la queue : Distibutor.java.
	- une class Java qui permet de faire le calcul de fibonaci et de
retourner le résultat : Worker.java.

Pour le lancer de l'application, il faut rajouter dans le fichier /ect/rc.local,
le lancement du script java-server_launch qui va exécuter le code source. 
Il faut aussi rediriger le flux du port 80 vers le port 8080 grâce à la commande 
"sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 8080"
suivi de servive iptables save.

Mon implémentation comporte deux projets maven, un pour le worker et un pour le 
loadbalancer. Un scritp bash est lancé au démarage de chaque instance initiale pour
lancer les classes java. Au démarrage, il y a une instance qui lance le serveur répartiteur
et une autre qui lance un worker.

On peut retrouver le code des deux projets sur github :
	- https://github.com/dzer45/AWS.git
	- https://github.com/dzer45/AWS-WORKER.git

En termes de performance, l'implémentation est assez rapide mais la création d'un nouveau 
worker et la création d'une queue de réponse pour chaque requête est un peu lourd.

Au niveau du passage à l'échelle, une montée en charge du nombre de requête sur le serveur
entraine la création de nouveau worker et inversement pour une diminution du nombre des requêtes
du coup, l'application est scalable.

Pour finir, l'application n'est pas tolérante au panne car si la queue de requête tombe, toute notre 
application tombe avec.

