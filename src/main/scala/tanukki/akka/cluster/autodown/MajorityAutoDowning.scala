package tanukki.akka.cluster.autodown

import akka.ConfigurationException
import akka.actor.{Address, Props, ActorSystem}
import akka.cluster.{Cluster, DowningProvider}
import scala.concurrent.Await
import scala.concurrent.duration._

class MajorityAutoDowning(system: ActorSystem) extends DowningProvider {
  private def clusterSettings = Cluster(system).settings

  override def downRemovalMargin: FiniteDuration = clusterSettings.DownRemovalMargin

  override def downingActorProps: Option[Props] = {
    val stableAfter = system.settings.config.getDuration("custom-downing.stable-after").toMillis millis
    val majorityMemberRole = {
      val r = system.settings.config.getString("custom-downing.majority-auto-downing.majority-member-role")
      if (r.isEmpty) None else Some(r)
    }
    val shutdownActorSystem = system.settings.config.getBoolean("custom-downing.majority-auto-downing.shutdown-actor-system-on-resolution")
    Some(MajorityAutoDown.props(majorityMemberRole, shutdownActorSystem, stableAfter))
  }
}

private[autodown] object MajorityAutoDown {
  def props(majorityMemberRole: Option[String], shutdownActorSystem: Boolean, autoDownUnreachableAfter: FiniteDuration): Props =
    Props(classOf[MajorityAutoDown], majorityMemberRole, shutdownActorSystem, autoDownUnreachableAfter)
}

private[autodown] class MajorityAutoDown(majorityMemberRole: Option[String], shutdownActorSystem: Boolean, autoDownUnreachableAfter: FiniteDuration)
  extends MajorityAutoDownBase(majorityMemberRole, autoDownUnreachableAfter) with ClusterCustomDowning {

  override def down(node: Address): Unit = {
    log.info("Majority is auto-downing unreachable node [{}]", node)
    cluster.down(node)
  }

  override def shutdownSelf(): Unit = {
    if (shutdownActorSystem) {
      Await.result(context.system.terminate(), 10 seconds)
    } else {
      throw new SplitBrainResolvedError("MajorityAutoDowning")
    }
  }
}
